package jeong.awsshop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.order.exception.OrderInvalidStatusTransitionException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Long userId;

    @Column(precision = 13, scale = 4, nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String shippingAddress;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private LocalDateTime completedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    public static Order createTemporary(Long userId) {
        long resolvedUserId = userId == null ? 1L : userId;
        LocalDateTime createdAt = LocalDateTime.now();

        return Order.builder()
            .userId(resolvedUserId)
            .status(OrderStatus.NOT_STARTED)
            .totalAmount(new BigDecimal("129.99"))
            .shippingAddress("Seoul Songpa-gu Olympic-ro 300")
            .createdAt(createdAt)
            .expiresAt(createdAt.plusMinutes(30))
            .build();
    }

    public static Order create(Long userId, List<OrderLine> lines) {
        long resolvedUserId = userId == null ? 1L : userId;
        LocalDateTime createdAt = LocalDateTime.now();
        BigDecimal totalAmount = lines.stream()
            .map(OrderLine::getLineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
            .userId(resolvedUserId)
            .status(OrderStatus.NOT_STARTED)
            .totalAmount(totalAmount)
            .shippingAddress("Seoul Songpa-gu Olympic-ro 300")
            .createdAt(createdAt)
            .expiresAt(createdAt.plusMinutes(30))
            .build();
        lines.forEach(order::addLine);
        return order;
    }

    private void addLine(OrderLine line) {
        line.assignOrder(this);
        this.lines.add(line);
    }

    public void pending() {
        validateTransition(OrderStatus.PENDING);
        this.status = OrderStatus.PENDING;
    }

    public void complete() {
        validateTransition(OrderStatus.COMPLETED);
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        validateTransition(OrderStatus.CANCELED);
        this.status = OrderStatus.CANCELED;
    }

    public void expire() {
        validateTransition(OrderStatus.EXPIRED);
        this.status = OrderStatus.EXPIRED;
    }

    private void validateTransition(OrderStatus targetStatus) {
        if (this.status == OrderStatus.CANCELED
            || this.status == OrderStatus.COMPLETED
            || this.status == OrderStatus.EXPIRED) {
            throw new OrderInvalidStatusTransitionException(this.status, targetStatus, this.id);
        }
    }
}
