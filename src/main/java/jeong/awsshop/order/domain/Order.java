package jeong.awsshop.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders") // "order"는 SQL에서 예약어이므로, 테이블 이름을 "orders"로 변경
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // 주문 상태 (NOT_STARTED, PROCESSING, COMPLETED, CANCELED)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // 주문 총 비용
    private BigDecimal totalAmount;

    // 주문 품목 (FK)


    // 구매자 정보 (FK)


    // 배송지 정보
    private String address;
}
