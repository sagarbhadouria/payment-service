package uno.y.paymentservice.exception;

/**
 * Thrown when a payment cannot be found by its ID.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }

    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}