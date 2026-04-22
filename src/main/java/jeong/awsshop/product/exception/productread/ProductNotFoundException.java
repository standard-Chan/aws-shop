package jeong.awsshop.product.exception.productread;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductNotFoundException extends ResponseStatusException {

    private static final String MESSAGE_PREFIX = "[Product 조회 실패]: ";
    private static final String DEFAULT_MESSAGE = MESSAGE_PREFIX + "존재하지 않는 상품입니다.";

    /**
     * Product 상세 조회 대상이 없을 때 404로 응답한다.
     */
    public ProductNotFoundException() {
        super(HttpStatus.NOT_FOUND, DEFAULT_MESSAGE);
    }

    /**
     * Product 상세 조회 대상 id를 포함해 404로 응답한다.
     */
    public ProductNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, MESSAGE_PREFIX + "존재하지 않는 상품입니다. id=" + id);
    }
}
