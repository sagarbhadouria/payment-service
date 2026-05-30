package uno.y.paymentservice.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uno.y.paymentservice.constant.PaymentMethod;
import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.exception.UnsupportedPaymentMethodException;
import uno.y.paymentservice.provider.PaymentProviderConnector;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes payment requests to the appropriate provider connector
 * based on the payment method.
 *
 * Routing rules:
 *   CARD → Provider A
 *   UPI  → Provider B
 *
 * Uses a Map-based lookup for O(1) routing — easily extensible
 * to support additional payment methods and providers without
 * modifying existing logic (Open/Closed Principle).
 */
@Component
public class PaymentRoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(PaymentRoutingEngine.class);

    // Maps PaymentProvider enum to its corresponding connector implementation
    private final Map<PaymentProvider, PaymentProviderConnector> providerConnectorMap;

    // Defines routing rules — which payment method maps to which provider
    private static final Map<PaymentMethod, PaymentProvider> ROUTING_RULES = Map.of(
            PaymentMethod.CARD, PaymentProvider.PROVIDER_A,
            PaymentMethod.UPI, PaymentProvider.PROVIDER_B
    );

    /**
     * Constructor — Spring injects all PaymentProviderConnector implementations.
     * Adding a new provider requires only creating a new connector bean,
     * no changes needed here.
     *
     * @param connectors all available PaymentProviderConnector beans
     */
    public PaymentRoutingEngine(List<PaymentProviderConnector> connectors) {
        this.providerConnectorMap = connectors.stream()
                .collect(Collectors.toMap(
                        PaymentProviderConnector::getProviderName,
                        Function.identity()
                ));
        log.info("Routing engine initialized with {} provider(s): {}",
                providerConnectorMap.size(), providerConnectorMap.keySet());
    }

    /**
     * Resolves the appropriate provider connector for the given payment method.
     *
     * @param paymentMethod the payment method (CARD or UPI)
     * @return the connector responsible for handling this payment method
     * @throws UnsupportedPaymentMethodException if no route exists for the payment method
     * @throws PaymentProviderException if the connector is not available
     */
    public PaymentProviderConnector resolveConnector(PaymentMethod paymentMethod) {
        log.debug("Resolving connector for payment method | paymentMethod={}", paymentMethod);

        PaymentProvider provider = getProviderForMethod(paymentMethod);

        PaymentProviderConnector connector = providerConnectorMap.get(provider);

        if (connector == null) {
            log.error("No connector found for provider | provider={}", provider);
            throw new PaymentProviderException(
                    provider.name(),
                    "Provider connector not available: " + provider
            );
        }

        log.info("Resolved connector | paymentMethod={} | provider={}",
                paymentMethod, provider);

        return connector;
    }

    /**
     * Returns the provider assigned to a given payment method.
     * Used by the service layer to set the provider on the payment entity.
     *
     * @param paymentMethod the payment method
     * @return the PaymentProvider enum value
     * @throws UnsupportedPaymentMethodException if no route exists
     */
    public PaymentProvider resolveProvider(PaymentMethod paymentMethod) {
        return getProviderForMethod(paymentMethod);
    }

    /**
     * Internal lookup — maps payment method to provider via routing rules.
     * Single source of truth for routing logic.
     *
     * @param paymentMethod the payment method to look up
     * @return the PaymentProvider enum value
     * @throws UnsupportedPaymentMethodException if no route exists for the payment method
     */
    private PaymentProvider getProviderForMethod(PaymentMethod paymentMethod) {
        PaymentProvider provider = ROUTING_RULES.get(paymentMethod);

        if (provider == null) {
            log.error("No routing rule found for payment method | paymentMethod={}",
                    paymentMethod);
            throw new UnsupportedPaymentMethodException(
                    "No provider configured for payment method: " + paymentMethod
            );
        }

        return provider;
    }
}