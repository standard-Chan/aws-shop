package jeong.awsshop.order.exception;

public class OrderNotFoundException extends OrderException {

    public OrderNotFoundException(Long id) {
        super("[Order] Order not found with id: " + id);
    }
}
