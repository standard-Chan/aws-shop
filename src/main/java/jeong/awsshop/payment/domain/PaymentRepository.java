package jeong.awsshop.payment.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findAllByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);

}
