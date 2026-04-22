package jeong.awsshop.product.exception.productread;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductNotFoundException extends ResponseStatusException {

    /**
     * Product 상세 조회 대상이 없을 때 404로 응답한다.
     */
    public ProductNotFoundException() {
        super(HttpStatus.NOT_FOUND, "[Product 조회 실패]: 존재하지 않는 상품입니다.");
    }
}
