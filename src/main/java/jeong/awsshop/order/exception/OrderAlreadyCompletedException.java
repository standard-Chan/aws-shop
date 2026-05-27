package jeong.awsshop.order.exception;

public class OrderAlreadyCompletedException extends OrderException {

    public OrderAlreadyCompletedException(Long id) {
        super("[Order] Completed order cannot be updated to EXECUTING. id=" + id);
    }
}
