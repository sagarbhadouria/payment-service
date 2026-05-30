package uno.y.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uno.y.paymentservice.constant.PaymentMethod;
import uno.y.paymentservice.constant.PaymentStatus;
import uno.y.paymentservice.dto.request.CreatePaymentRequest;
import uno.y.paymentservice.dto.response.CreatePaymentResponse;
import uno.y.paymentservice.dto.response.FetchPaymentResponse;
import uno.y.paymentservice.exception.IdempotencyConflictException;
import uno.y.paymentservice.exception.PaymentNotFoundException;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.exception.UnsupportedPaymentMethodException;
import uno.y.paymentservice.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    // ========== CREATE PAYMENT TESTS ==========

    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setPaymentId(UUID.randomUUID().toString());
        response.setStatus(PaymentStatus.PENDING);
        response.setCreatedAt(LocalDateTime.now());

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("test-key-123")))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "test-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(response.getPaymentId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shouldReturnBadRequestWhenMissingIdempotencyHeader() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Missing request header: Idempotency-Key")));
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsMissing() throws Exception {
        // Given
        String invalidRequest = "{\"currency\":\"USD\",\"paymentMethod\":\"CARD\"}";

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Amount is required")));
    }

    @Test
    void shouldReturnBadRequestWhenAmountIsZeroOrNegative() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Amount must be greater than 0")));
    }

    @Test
    void shouldReturnBadRequestWhenCurrencyIsInvalid() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("USDollars"); // not a 3-letter ISO code
        request.setPaymentMethod(PaymentMethod.CARD);

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Currency must be a valid 3-letter ISO code")));
    }

    @Test
    void shouldReturnBadRequestWhenPaymentMethodIsMissing() throws Exception {
        // Given
        String invalidRequest = "{\"amount\":100,\"currency\":\"USD\"}";

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Payment method is required")));
    }

    @Test
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("key-123")))
                .thenThrow(new RuntimeException("Provider timeout"));

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError()); // GlobalExceptionHandler maps generic Exception to 500
    }

    @Test
    void shouldReturnBadGatewayWhenProviderFailsWithPaymentProviderException() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("key-123")))
                .thenThrow(new PaymentProviderException("PROVIDER_A", "Connection refused"));

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("Provider PROVIDER_A error: Connection refused")));
    }

    @Test
    void shouldReturnBadRequestForUnsupportedPaymentMethod() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("key-123")))
                .thenThrow(new UnsupportedPaymentMethodException("No provider for method"));

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("No provider for method")));
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setPaymentMethod(PaymentMethod.CARD);

        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq("key-123")))
                .thenThrow(new IdempotencyConflictException("Different payload for same key"));

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Different payload for same key")));
    }

    // ========== FETCH PAYMENT TESTS ==========

    @Test
    void shouldFetchPaymentSuccessfully() throws Exception {
        // Given
        String paymentId = UUID.randomUUID().toString();
        FetchPaymentResponse response = new FetchPaymentResponse();
        response.setPaymentId(paymentId);
        response.setAmount(new BigDecimal("99.99"));
        response.setCurrency("USD");
        response.setPaymentMethod(PaymentMethod.CARD);
        response.setStatus(PaymentStatus.SUCCESS);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());

        when(paymentService.getPaymentById(paymentId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/v1/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.amount").value(99.99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        // Given
        String nonExistentId = "non-existent-id";
        when(paymentService.getPaymentById(nonExistentId))
                .thenThrow(new PaymentNotFoundException("Payment not found with ID: " + nonExistentId));

        // When & Then
        mockMvc.perform(get("/v1/payments/{paymentId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Payment not found")));
    }

    // ========== MALFORMED JSON ==========

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        // Given
        String malformedJson = "{\"amount\": \"not a number\", \"currency\": \"USD\"}";

        // When & Then
        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Malformed JSON request")));
    }
}