package uno.y.paymentservice.constant;

public enum PaymentProvider {
    PROVIDER_A("Card Payment Provider"),
    PROVIDER_B("UPI Payment Provider");

    private final String description;

    PaymentProvider(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}