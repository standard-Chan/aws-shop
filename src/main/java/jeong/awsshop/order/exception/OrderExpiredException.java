package jeong.awsshop.order.exception;

public class OrderExpiredException extends OrderException {

    public OrderExpiredException(Long id) {
        super("[Order] EXPIRED order cannot be updated. id=" + id);
    }
}
