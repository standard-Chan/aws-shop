package jeong.awsshop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_lines")
@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(precision = 13, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 13, scale = 4, nullable = false)
    private BigDecimal lineAmount;

    public static OrderLine create(Long productId, int quantity, BigDecimal unitPrice) {
        BigDecimal resolvedUnitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return OrderLine.builder()
            .productId(productId)
            .quantity(quantity)
            .unitPrice(resolvedUnitPrice)
            .lineAmount(resolvedUnitPrice.multiply(BigDecimal.valueOf(quantity)))
            .build();
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
