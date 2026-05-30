package uno.y.paymentservice.provider;

public class ProviderResponse {

    private final boolean success;
    private final String providerTransactionId;
    private final String message;

    private ProviderResponse(boolean success,
                             String providerTransactionId,
                             String message) {
        this.success = success;
        this.providerTransactionId = providerTransactionId;
        this.message = message;
    }

    public static ProviderResponse success(String providerTransactionId,
                                           String message) {
        return new ProviderResponse(true, providerTransactionId, message);
    }

    public static ProviderResponse failure(String message) {
        return new ProviderResponse(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public String getMessage() {
        return message;
    }
}