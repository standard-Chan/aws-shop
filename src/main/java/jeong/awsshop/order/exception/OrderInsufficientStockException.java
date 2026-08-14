package jeong.awsshop.order.exception;

public class OrderInsufficientStockException extends OrderException {

    public OrderInsufficientStockException(Long productId, int requestedQuantity, int currentQuantity) {
        super("[Order] 주문 상품 재고가 부족합니다. productId=" + productId
            + ", requestedQuantity=" + requestedQuantity
            + ", currentQuantity=" + currentQuantity);
    }
}
