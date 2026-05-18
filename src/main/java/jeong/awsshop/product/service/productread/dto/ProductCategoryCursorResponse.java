package jeong.awsshop.product.service.productread.dto;

import java.util.List;
import jeong.awsshop.product.service.productread.CategoryProductSort;
import jeong.awsshop.product.service.productread.CategoryProductDirection;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductCategoryCursorResponse(
        List<ProductSummaryResponse> products,
        CategoryCursor nextCursor,
        boolean hasNext
) {

    /**
     * sort 문자열 기준으로 category cursor 응답을 조립한다.
     */
    public static ProductCategoryCursorResponse from(
            List<ProductSummaryNativeProjection> rows,
            int size,
            CategoryProductSort sort,
            CategoryProductDirection direction
    ) {
        boolean hasNext = rows.size() > size;
        List<ProductSummaryResponse> products = rows.stream()
                .limit(size)
                .map(ProductSummaryResponse::from)
                .toList();

        CategoryCursor nextCursor = products.isEmpty()
                ? null
                : cursorFrom(products.getLast(), sort);

        return new ProductCategoryCursorResponse(products, nextCursor, hasNext);
    }

    /**
     * price 정렬이 아니면 기존 정렬 기준 이름으로 cursor 응답을 선택한다.
     */
    private static CategoryCursor cursorFrom(ProductSummaryResponse product, CategoryProductSort sort) {
        if (sort == CategoryProductSort.PRICE) {
            return CategoryCursor.priceCursor(product);
        }
        if (sort == CategoryProductSort.RATING_NUMBER) {
            return CategoryCursor.ratingNumberCursor(product);
        }
        return CategoryCursor.averageRatingCursor(product);
    }
}
