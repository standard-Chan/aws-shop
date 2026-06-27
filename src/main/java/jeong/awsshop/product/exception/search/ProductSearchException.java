package jeong.awsshop.product.exception.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ProductSearchException extends ResponseStatusException {

    private static final String MESSAGE_PREFIX = "[Product Elasticsearch 오류]: ";

    protected ProductSearchException(HttpStatus status, String reason) {
        super(status, MESSAGE_PREFIX + reason);
    }

    protected ProductSearchException(HttpStatus status, String reason, Throwable cause) {
        super(status, MESSAGE_PREFIX + reason, cause);
    }
}
