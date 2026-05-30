package uno.y.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uno.y.paymentservice.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {
}