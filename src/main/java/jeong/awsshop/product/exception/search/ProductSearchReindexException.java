package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;

public class ProductSearchReindexException extends ProductSearchException {

    private static final String MESSAGE = "검색 index 재색인에 실패했습니다.";

    public ProductSearchReindexException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE, cause);
    }
}
