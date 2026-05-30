package uno.y.paymentservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uno.y.paymentservice.constant.PaymentMethod;
import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.constant.PaymentStatus;
import uno.y.paymentservice.dto.request.CreatePaymentRequest;
import uno.y.paymentservice.dto.response.CreatePaymentResponse;
import uno.y.paymentservice.dto.response.FetchPaymentResponse;
import uno.y.paymentservice.entity.IdempotencyRecord;
import uno.y.paymentservice.entity.Payment;
import uno.y.paymentservice.exception.IdempotencyConflictException;
import uno.y.paymentservice.exception.PaymentNotFoundException;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.idempotency.IdempotencyService;
import uno.y.paymentservice.provider.PaymentProviderConnector;
import uno.y.paymentservice.provider.ProviderResponse;
import uno.y.paymentservice.repository.PaymentRepository;
import uno.y.paymentservice.routing.PaymentRoutingEngine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRoutingEngine routingEngine;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private CreatePaymentRequest validCardRequest;
    private CreatePaymentRequest validUpiRequest;
    private Payment persistedPayment;
    private PaymentProviderConnector mockConnector;
    private final String idempotencyKey = "test-idempotency-key";
    private final String requestHash = "abc123hash";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(paymentService, "retryDelayMs", 100L);

        validCardRequest = new CreatePaymentRequest();
        validCardRequest.setAmount(new BigDecimal("99.99"));
        validCardRequest.setCurrency("USD");
        validCardRequest.setPaymentMethod(PaymentMethod.CARD);

        validUpiRequest = new CreatePaymentRequest();
        validUpiRequest.setAmount(new BigDecimal("50.00"));
        validUpiRequest.setCurrency("INR");
        validUpiRequest.setPaymentMethod(PaymentMethod.UPI);

        persistedPayment = new Payment();
        persistedPayment.setPaymentId(UUID.randomUUID().toString());
        persistedPayment.setAmount(validCardRequest.getAmount());
        persistedPayment.setCurrency(validCardRequest.getCurrency());
        persistedPayment.setPaymentMethod(validCardRequest.getPaymentMethod());
        persistedPayment.setStatus(PaymentStatus.PENDING);
        persistedPayment.setCreatedAt(LocalDateTime.now());

        mockConnector = mock(PaymentProviderConnector.class);
        // Use lenient stubbing because not all tests use this mock
        lenient().when(mockConnector.getProviderName()).thenReturn(PaymentProvider.PROVIDER_A);
    }

    // ========== CREATE PAYMENT SUCCESS SCENARIOS ==========

    @Test
    void shouldCreateCardPaymentSuccessfully() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.CARD)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenReturn(ProviderResponse.success("PROVA-123", "Success"));

        CreatePaymentResponse response = paymentService.createPayment(validCardRequest, idempotencyKey);

        assertThat(response.getPaymentId()).isEqualTo(persistedPayment.getPaymentId());
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getCreatedAt()).isNotNull();

        // Verify that save was called at least once (status transitions are internal details)
        verify(paymentRepository, atLeast(2)).save(any(Payment.class));
        verify(idempotencyService).saveRecord(eq(idempotencyKey), eq(requestHash), eq(persistedPayment.getPaymentId()), any());
    }

    @Test
    void shouldCreateUpiPaymentSuccessfully() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.UPI)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenReturn(ProviderResponse.success("PROVB-456", "UPI Success"));

        CreatePaymentResponse response = paymentService.createPayment(validUpiRequest, idempotencyKey);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(routingEngine).resolveConnector(PaymentMethod.UPI);
    }

    // ========== IDEMPOTENCY TESTS ==========

    @Test
    void shouldReturnCachedResponseWhenIdempotencyKeyExistsWithSamePayload() {
        String cachedResponseBody = "{\"paymentId\":\"cached-id\",\"status\":\"SUCCESS\",\"createdAt\":\"2025-01-01T00:00:00\"}";
        IdempotencyRecord existingRecord = new IdempotencyRecord();
        existingRecord.setIdempotencyKey(idempotencyKey);
        existingRecord.setRequestHash(requestHash);
        existingRecord.setResponseBody(cachedResponseBody);

        when(idempotencyService.generateRequestHash(validCardRequest)).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingRecord));
        when(idempotencyService.deserializeResponse(eq(cachedResponseBody), eq(CreatePaymentResponse.class)))
                .thenReturn(new CreatePaymentResponse());

        paymentService.createPayment(validCardRequest, idempotencyKey);

        verify(paymentRepository, never()).save(any());
        verify(routingEngine, never()).resolveConnector(any());
        verify(idempotencyService).deserializeResponse(cachedResponseBody, CreatePaymentResponse.class);
    }

    @Test
    void shouldThrowIdempotencyConflictWhenSameKeyDifferentPayload() {
        String differentHash = "different-hash-456";
        IdempotencyRecord existingRecord = new IdempotencyRecord();
        existingRecord.setIdempotencyKey(idempotencyKey);
        existingRecord.setRequestHash(differentHash);

        when(idempotencyService.generateRequestHash(validCardRequest)).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingRecord));

        assertThatThrownBy(() -> paymentService.createPayment(validCardRequest, idempotencyKey))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Different payload for same key");

        verify(paymentRepository, never()).save(any());
    }

    // ========== RETRY LOGIC TESTS ==========

    @Test
    void shouldRetryOnFailureAndSucceedOnSecondAttempt() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.CARD)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenReturn(ProviderResponse.failure("Declined"))
                .thenReturn(ProviderResponse.success("PROVA-789", "Success after retry"));

        CreatePaymentResponse response = paymentService.createPayment(validCardRequest, idempotencyKey);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(mockConnector, times(2)).processPayment(any(Payment.class));
    }

    @Test
    void shouldFailAfterAllRetriesExhausted() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.CARD)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenReturn(ProviderResponse.failure("Declined"));

        CreatePaymentResponse response = paymentService.createPayment(validCardRequest, idempotencyKey);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(mockConnector, times(3)).processPayment(any(Payment.class));
    }

    @Test
    void shouldRetryWhenProviderThrowsException() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.CARD)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenThrow(new PaymentProviderException("PROVIDER_A", "Connection timeout"))
                .thenReturn(ProviderResponse.success("PROVA-999", "Recovered"));

        CreatePaymentResponse response = paymentService.createPayment(validCardRequest, idempotencyKey);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(mockConnector, times(2)).processPayment(any(Payment.class));
    }

    // ========== FETCH PAYMENT TESTS ==========

    @Test
    void shouldFetchExistingPaymentSuccessfully() {
        String paymentId = persistedPayment.getPaymentId();
        persistedPayment.setStatus(PaymentStatus.SUCCESS);
        persistedPayment.setProvider(PaymentProvider.PROVIDER_A);
        persistedPayment.setProviderTransactionId("PROVA-111");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(persistedPayment));

        FetchPaymentResponse response = paymentService.getPaymentById(paymentId);

        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getProvider()).isEqualTo(PaymentProvider.PROVIDER_A);
        assertThat(response.getProviderTransactionId()).isEqualTo("PROVA-111");
    }

    @Test
    void shouldThrowPaymentNotFoundWhenIdDoesNotExist() {
        String invalidId = "non-existent-id";
        when(paymentRepository.findById(invalidId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(invalidId))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Payment not found with ID: " + invalidId);
    }

    // ========== EDGE CASES ==========

    @Test
    void shouldPersistWithPendingStatusEvenIfProviderFailsLater() {
        when(idempotencyService.generateRequestHash(any())).thenReturn(requestHash);
        when(idempotencyService.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(persistedPayment);
        when(routingEngine.resolveConnector(PaymentMethod.CARD)).thenReturn(mockConnector);
        when(mockConnector.processPayment(any(Payment.class)))
                .thenThrow(new RuntimeException("Unexpected network error"));

        assertThatThrownBy(() -> paymentService.createPayment(validCardRequest, idempotencyKey))
                .isInstanceOf(RuntimeException.class);

        verify(paymentRepository, atLeastOnce()).save(any(Payment.class));
    }
}