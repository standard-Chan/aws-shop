package jeong.awsshop.stock.exception;

public class InsufficientStockException extends StockException {

    public InsufficientStockException(Long productId, int requestedQuantity, int currentQuantity) {
        super("[Stock] 재고가 부족합니다. productId=" + productId
            + ", requestedQuantity=" + requestedQuantity
            + ", currentQuantity=" + currentQuantity);
    }
}
