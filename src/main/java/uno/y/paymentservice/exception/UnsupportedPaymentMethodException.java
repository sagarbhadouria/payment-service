package uno.y.paymentservice.exception;

/**
 * Thrown when a payment method has no configured provider route.
 */
public class UnsupportedPaymentMethodException extends RuntimeException {

    public UnsupportedPaymentMethodException(String message) {
        super(message);
    }

    public UnsupportedPaymentMethodException(String message, Throwable cause) {
        super(message, cause);
    }
}