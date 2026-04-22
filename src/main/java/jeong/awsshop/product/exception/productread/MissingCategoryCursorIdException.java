package jeong.awsshop.product.exception.productread;

public class MissingCategoryCursorIdException extends ProductCategoryReadException {

    private static final String MESSAGE = "cursorId가 필요합니다.";

    /**
     * 정렬 cursor 값만 있고 cursorId가 없을 때 사용할 예외를 만든다.
     */
    public MissingCategoryCursorIdException() {
        super(MESSAGE);
    }
}
