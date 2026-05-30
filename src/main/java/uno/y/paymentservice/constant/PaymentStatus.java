package uno.y.paymentservice.constant;

public enum PaymentStatus {
    PENDING("Payment initiated, awaiting processing"),
    PROCESSING("Payment is being processed"),
    SUCCESS("Payment completed successfully"),
    FAILED("Payment processing failed");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}