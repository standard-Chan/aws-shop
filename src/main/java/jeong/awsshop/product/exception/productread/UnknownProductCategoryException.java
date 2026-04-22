package jeong.awsshop.product.exception.productread;

public class UnknownProductCategoryException extends ProductCategoryReadException {

    private static final String MESSAGE = "알 수 없는 category입니다.";

    /**
     * 알 수 없는 category 값일 때 사용할 예외를 만든다.
     */
    public UnknownProductCategoryException() {
        super(MESSAGE);
    }
}
