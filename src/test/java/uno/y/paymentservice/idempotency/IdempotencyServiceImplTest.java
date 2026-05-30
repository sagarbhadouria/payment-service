package uno.y.paymentservice.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import uno.y.paymentservice.dto.request.CreatePaymentRequest;
import uno.y.paymentservice.entity.IdempotencyRecord;
import uno.y.paymentservice.exception.IdempotencyConflictException;
import uno.y.paymentservice.repository.IdempotencyRecordRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyServiceImpl idempotencyService;

    private CreatePaymentRequest sampleRequest;
    private final String idempotencyKey = "key-123";
    private final String paymentId = "payment-456";
    private final String serializedRequest = "{\"amount\":100,\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}";
    private final String serializedResponse = "{\"paymentId\":\"payment-456\",\"status\":\"SUCCESS\"}";

    @BeforeEach
    void setUp() {
        sampleRequest = new CreatePaymentRequest();
        sampleRequest.setAmount(new BigDecimal("100.00"));
        sampleRequest.setCurrency("USD");
        sampleRequest.setPaymentMethod(uno.y.paymentservice.constant.PaymentMethod.CARD);
    }

    // ========== GENERATE REQUEST HASH TESTS ==========

    @Test
    void shouldGenerateConsistentHashForSameRequest() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(sampleRequest)).thenReturn(serializedRequest);

        // When
        String hash1 = idempotencyService.generateRequestHash(sampleRequest);
        String hash2 = idempotencyService.generateRequestHash(sampleRequest);

        // Then
        assertThat(hash1).isNotNull().isNotEmpty();
        assertThat(hash1).isEqualTo(hash2);
        // SHA-256 length in hex = 64 chars
        assertThat(hash1).hasSize(64);
    }

    @Test
    void shouldGenerateDifferentHashForDifferentRequests() throws JsonProcessingException {
        // Given
        CreatePaymentRequest otherRequest = new CreatePaymentRequest();
        otherRequest.setAmount(new BigDecimal("200.00"));
        otherRequest.setCurrency("EUR");
        otherRequest.setPaymentMethod(uno.y.paymentservice.constant.PaymentMethod.UPI);

        when(objectMapper.writeValueAsString(sampleRequest)).thenReturn(serializedRequest);
        when(objectMapper.writeValueAsString(otherRequest)).thenReturn("{\"amount\":200,\"currency\":\"EUR\",\"paymentMethod\":\"UPI\"}");

        // When
        String hash1 = idempotencyService.generateRequestHash(sampleRequest);
        String hash2 = idempotencyService.generateRequestHash(otherRequest);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSerializationFails() throws JsonProcessingException {
        // Given
        when(objectMapper.writeValueAsString(sampleRequest)).thenThrow(new JsonProcessingException("Mock serialization error") {});

        // When & Then
        assertThatThrownBy(() -> idempotencyService.generateRequestHash(sampleRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request cannot be serialized");
    }

    // ========== FIND BY IDEMPOTENCY KEY TESTS ==========

    @Test
    void shouldReturnEmptyOptionalWhenKeyNotFound() {
        // Given
        when(repository.findById(idempotencyKey)).thenReturn(Optional.empty());

        // When
        Optional<IdempotencyRecord> result = idempotencyService.findByIdempotencyKey(idempotencyKey);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnRecordWhenKeyExists() {
        // Given
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        when(repository.findById(idempotencyKey)).thenReturn(Optional.of(record));

        // When
        Optional<IdempotencyRecord> result = idempotencyService.findByIdempotencyKey(idempotencyKey);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    // ========== SAVE RECORD TESTS ==========

    @Test
    void shouldSaveRecordSuccessfully() throws JsonProcessingException {
        // Given
        CreatePaymentResponseStub response = new CreatePaymentResponseStub(paymentId, "SUCCESS");
        when(objectMapper.writeValueAsString(response)).thenReturn(serializedResponse);

        ArgumentCaptor<IdempotencyRecord> recordCaptor = ArgumentCaptor.forClass(IdempotencyRecord.class);

        // When
        idempotencyService.saveRecord(idempotencyKey, "requestHash123", paymentId, response);

        // Then
        verify(repository).save(recordCaptor.capture());
        IdempotencyRecord saved = recordCaptor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(saved.getRequestHash()).isEqualTo("requestHash123");
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getResponseBody()).isEqualTo(serializedResponse);
    }

    @Test
    void shouldThrowIdempotencyConflictOnDataIntegrityViolation() throws JsonProcessingException {
        // Given
        CreatePaymentResponseStub response = new CreatePaymentResponseStub(paymentId, "SUCCESS");
        when(objectMapper.writeValueAsString(response)).thenReturn(serializedResponse);
        when(repository.save(any(IdempotencyRecord.class))).thenThrow(new DataIntegrityViolationException("Duplicate key"));

        // When & Then
        assertThatThrownBy(() -> idempotencyService.saveRecord(idempotencyKey, "hash", paymentId, response))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Duplicate idempotency request detected");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenResponseSerializationFails() throws JsonProcessingException {
        // Given
        CreatePaymentResponseStub response = new CreatePaymentResponseStub(paymentId, "SUCCESS");
        when(objectMapper.writeValueAsString(response)).thenThrow(new JsonProcessingException("Serialization error") {});

        // When & Then
        assertThatThrownBy(() -> idempotencyService.saveRecord(idempotencyKey, "hash", paymentId, response))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot cache response");
    }

    // ========== DESERIALIZE RESPONSE TESTS ==========

    @Test
    void shouldDeserializeResponseSuccessfully() throws JsonProcessingException {
        // Given
        CreatePaymentResponseStub expected = new CreatePaymentResponseStub(paymentId, "SUCCESS");
        when(objectMapper.readValue(serializedResponse, CreatePaymentResponseStub.class)).thenReturn(expected);

        // When
        CreatePaymentResponseStub result = idempotencyService.deserializeResponse(serializedResponse, CreatePaymentResponseStub.class);

        // Then
        assertThat(result.getPaymentId()).isEqualTo(paymentId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenDeserializationFails() throws JsonProcessingException {
        // Given
        when(objectMapper.readValue(serializedResponse, CreatePaymentResponseStub.class))
                .thenThrow(new JsonProcessingException("Malformed JSON") {});

        // When & Then
        assertThatThrownBy(() -> idempotencyService.deserializeResponse(serializedResponse, CreatePaymentResponseStub.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Corrupted idempotency cache");
    }

    // ========== HELPER INNER CLASS FOR TESTING ==========
    // (simplified response DTO to avoid dragging actual response classes)

    static class CreatePaymentResponseStub {
        private String paymentId;
        private String status;

        public CreatePaymentResponseStub() {}
        public CreatePaymentResponseStub(String paymentId, String status) {
            this.paymentId = paymentId;
            this.status = status;
        }
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}