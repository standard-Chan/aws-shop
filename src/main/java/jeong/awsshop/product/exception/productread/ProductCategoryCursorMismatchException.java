package jeong.awsshop.product.exception.productread;

public class ProductCategoryCursorMismatchException extends ProductCategoryReadException {

    private static final String MESSAGE = "cursor category가 일치하지 않습니다.";

    /**
     * cursor 상품의 category가 요청 category와 다를 때 사용할 예외를 만든다.
     */
    public ProductCategoryCursorMismatchException() {
        super(MESSAGE);
    }
}
