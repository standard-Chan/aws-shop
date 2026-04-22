package jeong.awsshop.product.exception.productread;

public class MissingCategorySortCursorException extends ProductCategoryReadException {

    private static final String MESSAGE = "정렬 cursor 값이 필요합니다.";

    /**
     * cursorId만 있고 정렬 cursor 값이 없을 때 사용할 예외를 만든다.
     */
    public MissingCategorySortCursorException() {
        super(MESSAGE);
    }
}
