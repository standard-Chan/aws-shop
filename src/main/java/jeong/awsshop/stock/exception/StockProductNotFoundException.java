package jeong.awsshop.stock.exception;

public class StockProductNotFoundException extends StockException {

    public StockProductNotFoundException(Long productId) {
        super("[Stock] 재고를 추가할 상품이 존재하지 않습니다. productId=" + productId);
    }
}
