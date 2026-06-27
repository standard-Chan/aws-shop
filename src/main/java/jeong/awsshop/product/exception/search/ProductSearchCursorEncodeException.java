package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;

public class ProductSearchCursorEncodeException extends ProductSearchException {

    private static final String MESSAGE = "검색 cursor 생성에 실패했습니다.";

    public ProductSearchCursorEncodeException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE, cause);
    }
}
