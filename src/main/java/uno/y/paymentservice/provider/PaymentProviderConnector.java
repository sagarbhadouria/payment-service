package uno.y.paymentservice.provider;

import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.entity.Payment;

/**
 * Contract for payment provider connectors.
 * Implements the Strategy Pattern — allows the routing engine
 * to switch between providers at runtime without changing
 * orchestration logic.
 */
public interface PaymentProviderConnector {

    /**
     * Processes a payment through this provider.
     * Implements the Strategy Pattern — each connector handles
     * provider-specific logic while conforming to this contract.
     *
     * @param payment  domain payment object containing all payment details
     * @return         ProviderResponse containing outcome and transaction details
     * @throws PaymentProviderException if provider encounters an unexpected error
     */
    ProviderResponse  processPayment(Payment payment);

    /**
     * Returns the provider identifier for this connector.
     * Used by the routing engine and for logging/tracking.
     *
     * @return PaymentProvider enum value for this connector
     */
    PaymentProvider getProviderName();
}