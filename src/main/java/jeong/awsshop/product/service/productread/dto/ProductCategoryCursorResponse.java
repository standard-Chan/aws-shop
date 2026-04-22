package jeong.awsshop.product.service.productread.dto;

import java.util.List;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductCategoryCursorResponse(
        List<ProductSummaryResponse> products,
        CategoryCursor nextCursor,
        boolean hasNext
) {

    /**
     * category cursor 조회 결과를 정렬 기준에 맞는 cursor 응답으로 조립한다.
     */
    public static ProductCategoryCursorResponse from(
            List<ProductSummaryNativeProjection> rows,
            int size,
            boolean averageRatingSort
    ) {
        boolean hasNext = rows.size() > size;
        List<ProductSummaryResponse> products = rows.stream()
                .limit(size)
                .map(ProductSummaryResponse::from)
                .toList();

        CategoryCursor nextCursor = products.isEmpty()
                ? null
                : cursorFrom(products.getLast(), averageRatingSort);

        return new ProductCategoryCursorResponse(products, nextCursor, hasNext);
    }

    /**
     * 정렬 기준에 맞는 cursor 응답을 선택한다.
     */
    private static CategoryCursor cursorFrom(ProductSummaryResponse product, boolean averageRatingSort) {
        if (averageRatingSort) {
            return CategoryCursor.averageRatingCursor(product);
        }
        return CategoryCursor.ratingNumberCursor(product);
    }
}
