package uno.y.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uno.y.paymentservice.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}