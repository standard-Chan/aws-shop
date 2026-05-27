package jeong.awsshop.payment.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // TODO:
    // 현재는 orderId + EXECUTING 단건 조회만 가능하다.
    // createPayment 복구 플로우에서는 "가장 최근 처리 중 Payment 조회"와
    // "처리 중 Payment 부재 시 장애 복구 판단"에 필요한 조회식이 추가될 가능성이 높다.
    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

}
