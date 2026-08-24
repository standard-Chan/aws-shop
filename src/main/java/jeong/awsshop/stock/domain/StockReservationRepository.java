package jeong.awsshop.stock.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<StockReservationStatus> statuses);

    List<StockReservation> findAllByPaymentId(Long paymentId);

    List<StockReservation> findAllByPaymentIdAndStatus(Long paymentId, StockReservationStatus status);
}
