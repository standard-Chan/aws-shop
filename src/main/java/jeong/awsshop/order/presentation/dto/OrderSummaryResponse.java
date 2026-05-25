package jeong.awsshop.order.presentation.dto;

import java.math.BigDecimal;
import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderStatus;

public record OrderSummaryResponse(
    Long orderId,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    String shippingAddress
) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getShippingAddress()
        );
    }

}
