package jeong.awsshop.product.exception.productread;

public class ProductCategoryCursorNotFoundException extends ProductCategoryReadException {

    private static final String MESSAGE = "존재하지 않는 cursorId입니다.";

    /**
     * cursorId에 해당하는 상품이 없을 때 사용할 예외를 만든다.
     */
    public ProductCategoryCursorNotFoundException() {
        super(MESSAGE);
    }
}
