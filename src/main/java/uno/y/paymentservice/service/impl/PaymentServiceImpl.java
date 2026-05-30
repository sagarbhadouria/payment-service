package uno.y.paymentservice.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import uno.y.paymentservice.service.PaymentService;

import java.util.Optional;

/**
 * Core payment orchestration service.
 * Coordinates routing, provider processing, retry logic,
 * idempotency checks, and payment status tracking.
 * <p>
 * Flow:
 * 1. Check idempotency store — return cached response if exists
 * 2. Persist payment with PENDING status
 * 3. Route to appropriate provider
 * 4. Execute with retry logic
 * 5. Update payment status
 * 6. Cache response in idempotency store
 * 7. Return response
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentRoutingEngine routingEngine;
    private final IdempotencyService idempotencyService;

    @Value("${payment.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${payment.retry.delay-ms}")
    private long retryDelayMs;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentRoutingEngine routingEngine,
                              IdempotencyService idempotencyService) {
        this.paymentRepository = paymentRepository;
        this.routingEngine = routingEngine;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a new payment by orchestrating the full payment flow.
     * Idempotency is enforced — duplicate requests return cached response.
     *
     * @param request        payment details from the client
     * @param idempotencyKey unique key to prevent duplicate payments
     * @return CreatePaymentResponse with payment ID and status
     */
    @Override
    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request,
                                               String idempotencyKey) {
        log.info("Creating payment | idempotencyKey={} | amount={} {} | method={}",
                idempotencyKey, request.getAmount(),
                request.getCurrency(), request.getPaymentMethod());

        // Step 1 — Check idempotency store
        String requestHash = idempotencyService.generateRequestHash(request);
        Optional<IdempotencyRecord> existingRecord = idempotencyService.findByIdempotencyKey(idempotencyKey);

        if (existingRecord.isPresent()) {
            IdempotencyRecord idempotencyRecord = existingRecord.get();
            if (!idempotencyRecord.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Different payload for same key");
            }

            log.info("Duplicate request detected, returning cached response | idempotencyKey={}", idempotencyKey);
            return idempotencyService.deserializeResponse(
                    existingRecord.get().getResponseBody(),
                    CreatePaymentResponse.class
            );
        }

        // Step 2 — Persist payment with PENDING status
        Payment payment = buildPaymentEntity(request);
        payment = paymentRepository.save(payment);

        log.info("Payment persisted | paymentId={} | status={}",
                payment.getPaymentId(), payment.getStatus());

        // Step 3 — Resolve provider via routing engine
        PaymentProviderConnector connector = routingEngine
                .resolveConnector(request.getPaymentMethod());
        PaymentProvider provider =
                connector.getProviderName();

        // Step 4 — Update status to PROCESSING
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProvider(provider);
        payment = paymentRepository.save(payment);

        log.info("Payment routing to provider | paymentId={} | provider={}",
                payment.getPaymentId(), provider);

        // Step 5 — Execute with retry logic
        ProviderResponse providerResponse = executeWithRetry(connector, payment);

        // Step 6 — Update payment status based on provider response
        if (providerResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTransactionId(providerResponse.getProviderTransactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        payment = paymentRepository.save(payment);

        log.info("Payment completed | paymentId={} | status={} | provider={}",
                payment.getPaymentId(), payment.getStatus(), provider);

        // Step 7 — Build response
        CreatePaymentResponse response = buildCreatePaymentResponse(payment);

        // Step 8 — Cache in idempotency store
        idempotencyService.saveRecord(idempotencyKey, requestHash,
                payment.getPaymentId(), response);

        return response;
    }

    /**
     * Fetches the current state of an existing payment.
     *
     * @param paymentId unique identifier of the payment
     * @return FetchPaymentResponse with full payment details
     * @throws PaymentNotFoundException if payment does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public FetchPaymentResponse getPaymentById(String paymentId) {
        log.info("Fetching payment | paymentId={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.error("Payment not found | paymentId={}", paymentId);
                    return new PaymentNotFoundException(
                            "Payment not found with ID: " + paymentId
                    );
                });

        log.info("Payment fetched | paymentId={} | status={}",
                paymentId, payment.getStatus());

        return buildFetchPaymentResponse(payment);
    }

    /**
     * Executes payment processing with retry logic.
     * Retries up to maxRetryAttempts times with a delay between attempts.
     * Returns the last provider response after all attempts are exhausted.
     *
     * @param connector the provider connector to use
     * @param payment   the payment entity being processed
     * @return ProviderResponse from the last attempt
     */
    private ProviderResponse executeWithRetry(PaymentProviderConnector connector,
                                              Payment payment) {
        ProviderResponse response = null;
        int attempt = 0;

        while (attempt < maxRetryAttempts) {
            attempt++;
            log.info("Processing payment attempt {}/{} | paymentId={} | provider={}",
                    attempt, maxRetryAttempts,
                    payment.getPaymentId(), connector.getProviderName());

            try {
                response = connector.processPayment(payment);

                if (response.isSuccess()) {
                    log.info("Payment succeeded on attempt {}/{} | paymentId={}",
                            attempt, maxRetryAttempts, payment.getPaymentId());
                    return response;
                }

                log.warn("Payment attempt {}/{} failed | paymentId={} | reason={}",
                        attempt, maxRetryAttempts,
                        payment.getPaymentId(), response.getMessage());

            } catch (PaymentProviderException e) {
                log.error("Provider exception on attempt {}/{} | paymentId={} | provider={}",
                        attempt, maxRetryAttempts,
                        payment.getPaymentId(), connector.getProviderName(), e);
                response = ProviderResponse.failure(e.getMessage());
            }

            // Delay before next attempt — skip delay on last attempt
            if (attempt < maxRetryAttempts) {
                applyRetryDelay(payment.getPaymentId(), attempt);
            }
        }

        log.warn("All {} retry attempts exhausted | paymentId={}",
                maxRetryAttempts, payment.getPaymentId());

        if (response == null) {
            response = ProviderResponse.failure(
                    "Payment failed after all retry attempts"
            );
        }

        return response;
    }

    /**
     * Applies a delay between retry attempts.
     * Restores interrupt flag if the thread is interrupted during sleep.
     *
     * @param paymentId payment ID for logging context
     * @param attempt   current attempt number for logging
     */
    private void applyRetryDelay(String paymentId, int attempt) {
        try {
            log.debug("Waiting {}ms before retry attempt {} | paymentId={}",
                    retryDelayMs, attempt + 1, paymentId);
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry delay interrupted | paymentId={}", paymentId, e);
        }
    }

    /**
     * Maps Payment entity to CreatePaymentResponse.
     *
     * @param payment the saved payment entity
     * @return CreatePaymentResponse
     */
    private CreatePaymentResponse buildCreatePaymentResponse(Payment payment) {
        CreatePaymentResponse response = new CreatePaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }

    /**
     * Maps Payment entity to FetchPaymentResponse.
     *
     * @param payment the fetched payment entity
     * @return FetchPaymentResponse
     */
    private FetchPaymentResponse buildFetchPaymentResponse(Payment payment) {
        FetchPaymentResponse response = new FetchPaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setProvider(payment.getProvider());
        response.setProviderTransactionId(payment.getProviderTransactionId());
        response.setStatus(payment.getStatus());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }

    private Payment buildPaymentEntity(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}