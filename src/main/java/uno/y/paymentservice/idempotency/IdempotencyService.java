package uno.y.paymentservice.idempotency;

import uno.y.paymentservice.entity.IdempotencyRecord;

import java.util.Optional;

/**
 * Contract for idempotency operations.
 * Handles storing, retrieving and hashing of idempotency records.
 */
public interface IdempotencyService {

    /**
     * Generates a hash of the request body.
     * Used to detect same idempotency key reused with different payload.
     */
    String generateRequestHash(Object request);

    /**
     * Finds an existing idempotency record by key.
     * Throws if same key is used with a different request hash.
     *
     * @param idempotencyKey the client-provided idempotency key
     * @return Optional containing the record if found, or empty if not found
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Saves an idempotency record after successful payment creation.
     *
     * @param idempotencyKey the client-provided key
     * @param requestHash    hash of the request
     * @param paymentId      ID of the created payment
     * @param response       the response to cache
     */
    void saveRecord(String idempotencyKey, String requestHash,
                    String paymentId, Object response);

    /**
     * Deserializes a cached JSON response back to the target type.
     *
     * @param responseBody cached JSON string
     * @param targetClass  the class to deserialize into
     * @return deserialized response object
     */
    <T> T deserializeResponse(String responseBody, Class<T> targetClass);
}