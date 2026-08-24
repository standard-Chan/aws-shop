package jeong.awsshop.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import jeong.awsshop.stock.exception.InvalidStockQuantityException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "stock_reservation",
    indexes = @Index(
        name = "idx_stock_reservation_payment_status",
        columnList = "payment_id,status"
    ),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_reservation_payment_product",
        columnNames = {"payment_id", "product_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class StockReservation {

    @Id
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime restoredAt;

    public static StockReservation reserve(Long id, Long paymentId, Long orderId, Long productId, int quantity) {
        validateQuantity(quantity);
        return StockReservation.builder()
            .id(id)
            .paymentId(paymentId)
            .orderId(orderId)
            .productId(productId)
            .quantity(quantity)
            .status(StockReservationStatus.RESERVED)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public void complete() {
        if (this.status != StockReservationStatus.RESERVED) {
            return;
        }
        this.status = StockReservationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void restore() {
        if (this.status != StockReservationStatus.RESERVED) {
            return;
        }
        this.status = StockReservationStatus.RESTORED;
        this.restoredAt = LocalDateTime.now();
    }

    public boolean isReserved() {
        return this.status == StockReservationStatus.RESERVED;
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidStockQuantityException(quantity);
        }
    }
}
