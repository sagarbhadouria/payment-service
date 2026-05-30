package uno.y.paymentservice.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.entity.Payment;
import uno.y.paymentservice.exception.PaymentProviderException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Connector for Provider A — handles CARD payments.
 * Implements PaymentProviderConnector (Strategy Pattern).
 * Simulates an external payment provider with realistic
 * success/failure behavior for testing and demonstration purposes.
 */
@Component
public class ProviderAConnector implements PaymentProviderConnector {

    private static final Logger log = LoggerFactory.getLogger(ProviderAConnector.class);

    // Simulated provider success rate for retry/failover testing
    private static final double SUCCESS_RATE = 0.8;

    /**
     * Processes a CARD payment through Provider A.
     * Simulates network latency and realistic success/failure outcomes.
     *
     * @param payment domain payment object containing all payment details
     * @return ProviderResponse containing outcome and provider transaction details
     * @throws PaymentProviderException if provider encounters an unexpected error
     */
    @Override
    public ProviderResponse processPayment(Payment payment) {
        log.info("Provider A processing CARD payment | paymentId={} | amount={} {}",
                payment.getPaymentId(), payment.getAmount(), payment.getCurrency());

        try {
            // Simulate network latency of a real provider call (100-300ms)
            simulateNetworkLatency();

            // Simulate provider response based on success rate
            boolean success = ThreadLocalRandom.current().nextDouble() < SUCCESS_RATE;

            if (success) {
                String providerTransactionId = "PROVA-" + UUID.randomUUID();
                log.info("Provider A payment successful | paymentId={} | providerTxnId={}",
                        payment.getPaymentId(), providerTransactionId);
                return ProviderResponse.success(
                        providerTransactionId,
                        "Payment processed successfully by Provider A"
                );
            } else {
                log.warn("Provider A payment declined | paymentId={}",
                        payment.getPaymentId());
                return ProviderResponse.failure(
                        "Payment declined by Provider A"
                );
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Provider A interrupted during processing | paymentId={}",
                    payment.getPaymentId(), e);
            throw new PaymentProviderException(
                    PaymentProvider.PROVIDER_A.name(),
                    "Provider A was interrupted during payment processing"
            );
        } catch (Exception e) {
            log.error("Provider A unexpected error | paymentId={}",
                    payment.getPaymentId(), e);
            throw new PaymentProviderException(
                    PaymentProvider.PROVIDER_A.name(),
                    "Provider A encountered an unexpected error: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Returns the provider identifier for this connector.
     * Used by the routing engine to identify and select this provider.
     */
    @Override
    public PaymentProvider getProviderName() {
        return PaymentProvider.PROVIDER_A;
    }

    /**
     * Simulates realistic network latency for an external provider call.
     * Latency range: 100ms - 300ms
     *
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    private void simulateNetworkLatency() throws InterruptedException {
        long latency = ThreadLocalRandom.current().nextLong(100, 301);
        log.debug("Provider A simulating network latency | latency={}ms", latency);
        Thread.sleep(latency);
    }
}