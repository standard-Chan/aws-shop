package jeong.awsshop.stock.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.payment.infrastructure.order.dto.OrderLineSummary;
import jeong.awsshop.stock.domain.StockReservation;
import jeong.awsshop.stock.domain.StockReservationRepository;
import jeong.awsshop.stock.domain.StockReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockReservationService {

    private final StockService stockService;
    private final StockReservationRepository stockReservationRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public void reserve(Long paymentId, Long orderId, List<OrderLineSummary> orderLines) {
        Map<Long, Integer> quantitiesByProductId = aggregateQuantities(orderLines);

        for (Map.Entry<Long, Integer> entry : quantitiesByProductId.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            stockService.decrease(productId, quantity);
            stockReservationRepository.save(
                StockReservation.reserve(
                    snowflakeIdGenerator.nextId(),
                    paymentId,
                    orderId,
                    productId,
                    quantity
                )
            );
        }
    }

    @Transactional
    public void complete(Long paymentId) {
        List<StockReservation> reservations = stockReservationRepository.findAllByPaymentIdAndStatus(
            paymentId,
            StockReservationStatus.RESERVED
        );
        // 조회한 예약 엔티티의 상태 변경을 JPA dirty checking으로 저장하기 위해 트랜잭션이 필요하다.
        reservations.forEach(StockReservation::complete);
    }

    @Transactional
    public void restore(Long paymentId) {
        List<StockReservation> reservations = stockReservationRepository.findAllByPaymentIdAndStatus(
            paymentId,
            StockReservationStatus.RESERVED
        );
        for (StockReservation reservation : reservations) {
            stockService.increase(reservation.getProductId(), reservation.getQuantity());
            reservation.restore();
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAnyReservation(Long paymentId) {
        return stockReservationRepository.existsByPaymentIdAndStatusIn(
            paymentId,
            List.of(
                StockReservationStatus.RESERVED,
                StockReservationStatus.COMPLETED,
                StockReservationStatus.RESTORED
            )
        );
    }

    private Map<Long, Integer> aggregateQuantities(List<OrderLineSummary> orderLines) {
        Map<Long, Integer> quantitiesByProductId = new LinkedHashMap<>();
        for (OrderLineSummary line : orderLines) {
            quantitiesByProductId.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return quantitiesByProductId;
    }
}
