package uno.y.paymentservice.service;

import uno.y.paymentservice.dto.request.CreatePaymentRequest;
import uno.y.paymentservice.dto.response.CreatePaymentResponse;
import uno.y.paymentservice.dto.response.FetchPaymentResponse;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.exception.UnsupportedPaymentMethodException;

/**
 * Contract for payment orchestration service.
 * Defines the core operations for creating and fetching payments.
 */
public interface PaymentService {

    /**
     * Creates a new payment by orchestrating routing,
     * provider processing, retry logic and idempotency checks.
     *
     * @param request        payment details from the client
     * @param idempotencyKey unique key to prevent duplicate payments
     * @return CreatePaymentResponse with payment ID and status
     * @throws UnsupportedPaymentMethodException if payment method has no route
     * @throws PaymentProviderException if provider fails after all retries
     */
    CreatePaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey);

    /**
     * Fetches the current state of an existing payment.
     *
     * @param paymentId unique identifier of the payment
     * @return FetchPaymentResponse with full payment details
     */
    FetchPaymentResponse getPaymentById(String paymentId);
}