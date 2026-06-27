package jeong.awsshop.product.service.search.cursor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jeong.awsshop.product.service.search.criteria.ProductSearchDirection;
import jeong.awsshop.product.service.search.criteria.ProductSearchSort;
import jeong.awsshop.product.service.search.document.ProductSearchDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductSearchCursorCodec {

    private final ObjectMapper objectMapper;

    public ProductSearchCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(ProductSearchSort sort, ProductSearchDirection direction, ProductSearchDocument document) {
        ProductSearchCursor cursor = new ProductSearchCursor(
                sort,
                direction,
                document.id(),
                sortValue(sort, document)
        );
        try {
            byte[] json = objectMapper.writeValueAsBytes(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to encode product search cursor", e);
        }
    }

    public ProductSearchCursor decode(
            String token,
            ProductSearchSort requestedSort,
            ProductSearchDirection requestedDirection
    ) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(token);
            ProductSearchCursor cursor = objectMapper.readValue(
                    new String(json, StandardCharsets.UTF_8),
                    ProductSearchCursor.class
            );
            validate(cursor, requestedSort, requestedDirection);
            return cursor;
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw invalidCursor(e);
        }
    }

    private void validate(
            ProductSearchCursor cursor,
            ProductSearchSort requestedSort,
            ProductSearchDirection requestedDirection
    ) {
        if (cursor.sort() != requestedSort || cursor.direction() != requestedDirection
                || cursor.id() == null || cursor.sortValue() == null) {
            throw invalidCursor(null);
        }
    }

    private String sortValue(ProductSearchSort sort, ProductSearchDocument document) {
        return switch (sort) {
            case AVERAGE_RATING -> decimalString(document.averageRating());
            case PRICE -> decimalString(document.price());
            case RATING_NUMBER -> String.valueOf(document.ratingNumber());
        };
    }

    private String decimalString(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.toPlainString();
    }

    private ResponseStatusException invalidCursor(Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product search cursor", cause);
    }
}
