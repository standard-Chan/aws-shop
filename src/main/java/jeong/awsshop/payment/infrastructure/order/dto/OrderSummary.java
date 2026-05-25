package jeong.awsshop.payment.infrastructure.order.dto;

import java.math.BigDecimal;
import jeong.awsshop.order.domain.OrderStatus;

/**
 * 주문 요약 정보를 담는 DTO 클래스입니다.
 * 사용 시점 : ORDER 정보를, ORDER SERVER로 부터 받아올 때 사용됩니다.
 * 사용 목적 : Order 정보를 받아, 결제 정보를 생성합니다.
 */
public record OrderSummary(
    Long orderId,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    String shippingAddress
) {}