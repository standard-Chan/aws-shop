package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;

public class ProductSearchIndexPreparationException extends ProductSearchException {

    private static final String MESSAGE = "검색 index 준비에 실패했습니다.";

    public ProductSearchIndexPreparationException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE, cause);
    }
}
