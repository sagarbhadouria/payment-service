package uno.y.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String paymentId;

    // Hash of the original request body — used to detect same key with different payload
    @Column(nullable = false)
    private String requestHash;

    // Cached JSON response — returned on duplicate requests
    @Column(nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public IdempotencyRecord() {
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
