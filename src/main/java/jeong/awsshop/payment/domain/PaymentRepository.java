package jeong.awsshop.payment.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findAllByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime createdAt);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE payment
        SET status = 'EXECUTING',
            payment_key = :paymentKey
        WHERE id = :paymentId
          AND status = 'NOT_STARTED'
        """, nativeQuery = true)
    int startConfirmIfNotStarted(
        @Param("paymentId") Long paymentId,
        @Param("paymentKey") String paymentKey
    );
}
