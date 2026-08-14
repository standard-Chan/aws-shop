package jeong.awsshop.order.exception;

public class OrderProductNotFoundException extends OrderException {

    public OrderProductNotFoundException(Long productId) {
        super("[Order] 주문 상품이 존재하지 않습니다. productId=" + productId);
    }
}
