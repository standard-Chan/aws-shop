package jeong.awsshop.product.exception.productread;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductCategoryReadException extends ResponseStatusException {

    private static final String MESSAGE_PREFIX = "[Category 조회 실패]: ";

    /**
     * category 조회 실패를 400 Bad Request로 응답한다.
     */
    protected ProductCategoryReadException(String reason) {
        super(HttpStatus.BAD_REQUEST, MESSAGE_PREFIX + reason);
    }
}
