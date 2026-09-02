package jeong.awsshop.stock.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<StockReservationStatus> statuses);

    List<StockReservation> findAllByPaymentId(Long paymentId);

    List<StockReservation> findAllByPaymentIdAndStatus(Long paymentId, StockReservationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE StockReservation reservation
        SET reservation.status = 'RESTORED',
            reservation.restoredAt = :restoredAt
        WHERE reservation.id = :reservationId
          AND reservation.status = 'RESERVED'
        """)
    int markRestoredIfReserved(
        @Param("reservationId") Long reservationId,
        @Param("restoredAt") LocalDateTime restoredAt
    );
}
