package jeong.awsshop.stock.exception;

public class StockNotFoundException extends StockException {

    public StockNotFoundException(Long productId) {
        super("[Stock] 재고가 존재하지 않습니다. productId=" + productId);
    }
}
