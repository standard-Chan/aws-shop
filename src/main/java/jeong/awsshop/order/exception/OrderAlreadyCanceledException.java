package jeong.awsshop.order.exception;

public class OrderAlreadyCanceledException extends OrderException {

    public OrderAlreadyCanceledException(Long id) {
        super("[Order] CANCELED order cannot be updated. id=" + id);
    }
}
