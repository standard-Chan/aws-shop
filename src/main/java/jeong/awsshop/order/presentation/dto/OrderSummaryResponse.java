package jeong.awsshop.order.presentation.dto;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.order.domain.Order;
import jeong.awsshop.order.domain.OrderStatus;

public record OrderSummaryResponse(
    Long orderId,
    Long userId,
    OrderStatus status,
    BigDecimal totalAmount,
    String shippingAddress,
    List<OrderLineResponse> items
) {

    public OrderSummaryResponse(
        Long orderId,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String shippingAddress
    ) {
        this(orderId, userId, status, totalAmount, shippingAddress, List.of());
    }

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getShippingAddress(),
            order.getLines().stream()
                .map(OrderLineResponse::from)
                .toList()
        );
    }

}
