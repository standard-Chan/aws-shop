package jeong.awsshop.payment.domain.dto;

import java.math.BigDecimal;

/**
 * 주문 요약 정보를 담는 DTO 클래스입니다.
 * 사용 시점 : ORDER 정보를, ORDER SERVER로 부터 받아올 때 사용됩니다.
 * 사용 목적 : Order 정보를 받아, 결제 정보를 생성합니다.
 */
public class OrderSummary {
    Long orderId;
    BigDecimal totalPrice;

    // order domain에서 필요한 데이터를 임시로 생성
    static public OrderSummary createTempOrderSummary() {
            OrderSummary orderSummary = new OrderSummary();
            orderSummary.orderId = 1L;
            orderSummary.totalPrice = new BigDecimal("100.00");
            return orderSummary;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
}
