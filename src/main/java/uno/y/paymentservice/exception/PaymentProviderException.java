package uno.y.paymentservice.exception;

public class PaymentProviderException extends RuntimeException {

    private final String providerName;

    public PaymentProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public PaymentProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}