package jeong.awsshop.order.exception;

public class OrderStockNotFoundException extends OrderException {

    public OrderStockNotFoundException(Long productId) {
        super("[Order] 주문 상품 재고가 존재하지 않습니다. productId=" + productId);
    }
}
