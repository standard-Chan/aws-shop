package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;

public class ProductSearchInvalidCursorException extends ProductSearchException {

    private static final String MESSAGE = "검색 cursor가 올바르지 않습니다.";

    public ProductSearchInvalidCursorException() {
        super(HttpStatus.BAD_REQUEST, MESSAGE);
    }

    public ProductSearchInvalidCursorException(Throwable cause) {
        super(HttpStatus.BAD_REQUEST, MESSAGE, cause);
    }
}
