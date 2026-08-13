package jeong.awsshop.stock.exception;

public class InvalidStockQuantityException extends StockException {

    public InvalidStockQuantityException(int quantity) {
        super("[Stock] 재고 변경 수량은 양수여야 합니다. quantity=" + quantity);
    }
}
