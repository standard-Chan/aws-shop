package jeong.awsshop.product.service.search.cursor;

import jeong.awsshop.product.service.search.criteria.ProductSearchDirection;
import jeong.awsshop.product.service.search.criteria.ProductSearchSort;

public record ProductSearchCursor(
        ProductSearchSort sort,
        ProductSearchDirection direction,
        Long id,
        String sortValue
) {
}
