package uno.y.paymentservice.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uno.y.paymentservice.entity.IdempotencyRecord;
import uno.y.paymentservice.exception.IdempotencyConflictException;
import uno.y.paymentservice.repository.IdempotencyRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Implementation of IdempotencyService using database storage,
 * SHA‑256 request hashing, and Jackson JSON serialization.
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyServiceImpl(IdempotencyRecordRepository repository,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateRequestHash(Object request) {
        try {
            // Serialize request to JSON
            String json = objectMapper.writeValueAsString(request);

            // Compute SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request for hashing", e);
            throw new IllegalArgumentException("Request cannot be serialized", e);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in Java
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey) {
        return repository.findById(idempotencyKey);
    }

    @Override
    @Transactional
    public void saveRecord(String idempotencyKey, String requestHash,
                           String paymentId, Object response) {
        try {
            String responseBody = objectMapper.writeValueAsString(response);
            IdempotencyRecord idempotencyRecord = new IdempotencyRecord();
            idempotencyRecord.setIdempotencyKey(idempotencyKey);
            idempotencyRecord.setRequestHash(requestHash);
            idempotencyRecord.setPaymentId(paymentId);
            idempotencyRecord.setResponseBody(responseBody);
            repository.save(idempotencyRecord);
            log.info("Idempotency record saved | key={} | paymentId={}", idempotencyKey, paymentId);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate idempotency key detected | key={}", idempotencyKey);
            throw new IdempotencyConflictException("Duplicate idempotency request detected");
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency cache | key={}", idempotencyKey, e);
            throw new RuntimeException("Cannot cache response", e);
        }
    }

    @Override
    public <T> T deserializeResponse(String responseBody, Class<T> targetClass) {
        try {
            return objectMapper.readValue(responseBody, targetClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached response", e);
            throw new RuntimeException("Corrupted idempotency cache", e);
        }
    }
}