package jeong.awsshop.stock.exception;

public class StockQuantityOverflowException extends StockException {

    public StockQuantityOverflowException(Long productId, int currentQuantity, int addedQuantity) {
        super("[Stock] 재고 수량이 허용 범위를 초과합니다. productId=" + productId
            + ", currentQuantity=" + currentQuantity
            + ", addedQuantity=" + addedQuantity);
    }
}
