package jeong.awsshop.order.exception;

import jeong.awsshop.order.domain.OrderStatus;

public class OrderInvalidStatusTransitionException extends OrderException {

    public OrderInvalidStatusTransitionException(OrderStatus from, OrderStatus to, Long orderId) {
        super("[Order] 변경할 수 없는 Order status 입니다. from=" + from + ", to=" + to + ", id=" + orderId);
    }
}
