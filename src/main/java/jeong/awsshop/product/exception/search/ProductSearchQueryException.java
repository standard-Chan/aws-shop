package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;

public class ProductSearchQueryException extends ProductSearchException {

    private static final String MESSAGE = "Elasticsearch 상품 검색 요청에 실패했습니다.";

    public ProductSearchQueryException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE, cause);
    }
}
