package uno.y.paymentservice.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uno.y.paymentservice.constant.PaymentProvider;
import uno.y.paymentservice.entity.Payment;
import uno.y.paymentservice.exception.PaymentProviderException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderAConnectorTest {

    @InjectMocks
    private ProviderAConnector connector;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setAmount(new BigDecimal("99.99"));
        payment.setCurrency("USD");
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    void shouldReturnSuccessResponseWhenRandomNumberWithinSuccessRate() {
        try (MockedStatic<ThreadLocalRandom> mockedRandom = Mockito.mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom mockRandom = mock(ThreadLocalRandom.class);
            mockedRandom.when(ThreadLocalRandom::current).thenReturn(mockRandom);
            when(mockRandom.nextDouble()).thenReturn(0.5); // 0.5 < 0.8 -> success
            when(mockRandom.nextLong(anyLong(), anyLong())).thenReturn(150L);

            ProviderResponse response = connector.processPayment(payment);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getProviderTransactionId()).startsWith("PROVA-");
            assertThat(response.getMessage()).contains("successfully");
        }
    }

    @Test
    void shouldReturnFailureResponseWhenRandomNumberExceedsSuccessRate() {
        try (MockedStatic<ThreadLocalRandom> mockedRandom = Mockito.mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom mockRandom = mock(ThreadLocalRandom.class);
            mockedRandom.when(ThreadLocalRandom::current).thenReturn(mockRandom);
            when(mockRandom.nextDouble()).thenReturn(0.9); // 0.9 > 0.8 -> failure
            when(mockRandom.nextLong(anyLong(), anyLong())).thenReturn(150L);

            ProviderResponse response = connector.processPayment(payment);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getProviderTransactionId()).isNull();
            assertThat(response.getMessage()).isEqualTo("Payment declined by Provider A");
        }
    }

    // ========== EXCEPTION HANDLING ==========

    @Test
    void shouldThrowPaymentProviderExceptionWhenGenericExceptionOccurs() {
        try (MockedStatic<ThreadLocalRandom> mockedRandom = Mockito.mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom mockRandom = mock(ThreadLocalRandom.class);
            mockedRandom.when(ThreadLocalRandom::current).thenReturn(mockRandom);
            when(mockRandom.nextLong(anyLong(), anyLong())).thenThrow(new RuntimeException("Network error"));

            assertThatThrownBy(() -> connector.processPayment(payment))
                    .isInstanceOf(PaymentProviderException.class)
                    .hasMessageContaining("Provider A encountered an unexpected error: Network error")
                    .satisfies(ex -> {
                        PaymentProviderException pEx = (PaymentProviderException) ex;
                        assertThat(pEx.getProviderName()).isEqualTo(PaymentProvider.PROVIDER_A.name());
                    });
        }
    }

    // ========== PROVIDER NAME ==========

    @Test
    void shouldReturnProviderAName() {
        assertThat(connector.getProviderName()).isEqualTo(PaymentProvider.PROVIDER_A);
    }
}