package jeong.awsshop.order.exception;

public class OrderAlreadyExecutingException extends OrderException {

    public OrderAlreadyExecutingException(Long id) {
        super("[Order] Order is already EXECUTING. id=" + id);
    }
}
