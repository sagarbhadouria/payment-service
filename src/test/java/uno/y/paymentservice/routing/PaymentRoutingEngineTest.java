package uno.y.paymentservice.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uno.y.paymentservice.constant.PaymentMethod;
import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.exception.PaymentProviderException;
import uno.y.paymentservice.provider.PaymentProviderConnector;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRoutingEngineTest {

    @Mock
    private PaymentProviderConnector providerAConnector;

    @Mock
    private PaymentProviderConnector providerBConnector;

    private PaymentRoutingEngine routingEngine;

    @BeforeEach
    void setUp() {
        when(providerAConnector.getProviderName()).thenReturn(PaymentProvider.PROVIDER_A);
        when(providerBConnector.getProviderName()).thenReturn(PaymentProvider.PROVIDER_B);
        routingEngine = new PaymentRoutingEngine(List.of(providerAConnector, providerBConnector));
    }

    // ========== RESOLVE CONNECTOR TESTS ==========

    @Test
    void shouldResolveConnectorForCardToProviderA() {
        PaymentProviderConnector connector = routingEngine.resolveConnector(PaymentMethod.CARD);
        assertThat(connector).isEqualTo(providerAConnector);
    }

    @Test
    void shouldResolveConnectorForUpiToProviderB() {
        PaymentProviderConnector connector = routingEngine.resolveConnector(PaymentMethod.UPI);
        assertThat(connector).isEqualTo(providerBConnector);
    }

    // ========== RESOLVE PROVIDER TESTS ==========

    @Test
    void shouldResolveProviderForCardAsProviderA() {
        PaymentProvider provider = routingEngine.resolveProvider(PaymentMethod.CARD);
        assertThat(provider).isEqualTo(PaymentProvider.PROVIDER_A);
    }

    @Test
    void shouldResolveProviderForUpiAsProviderB() {
        PaymentProvider provider = routingEngine.resolveProvider(PaymentMethod.UPI);
        assertThat(provider).isEqualTo(PaymentProvider.PROVIDER_B);
    }

    // ========== UNSUPPORTED PAYMENT METHOD TESTS ==========

    @Test
    void shouldThrowNullPointerExceptionForNullMethod() {
        // Since the routing engine uses Map.of() which does not allow null keys,
        // null payment method throws NullPointerException. Controller validation
        // ensures @NotNull, so this is acceptable.
        assertThatThrownBy(() -> routingEngine.resolveConnector(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ========== MISSING CONNECTOR TESTS ==========

    @Test
    void shouldThrowPaymentProviderExceptionWhenConnectorNotAvailable() {
        PaymentRoutingEngine engineMissingA = new PaymentRoutingEngine(List.of(providerBConnector));
        assertThatThrownBy(() -> engineMissingA.resolveConnector(PaymentMethod.CARD))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessageContaining("Provider connector not available: PROVIDER_A");
    }

    // ========== CONSTRUCTOR & MAP INITIALIZATION TESTS ==========

    @Test
    void shouldInitializeProviderMapCorrectly() {
        assertThat(routingEngine.resolveConnector(PaymentMethod.CARD)).isEqualTo(providerAConnector);
        assertThat(routingEngine.resolveConnector(PaymentMethod.UPI)).isEqualTo(providerBConnector);
    }
}