package jeong.awsshop.product.service.productread.dto;

import java.util.List;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductCursorResponse(
        List<ProductSummaryResponse> products,
        String nextCursorId,
        boolean hasNext
) {

    /**
     * Product Pagination 조회 결과를 응답으로 조립한다.
     */
    public static ProductCursorResponse from(List<ProductSummaryNativeProjection> rows, int size) {
        boolean hasNext = rows.size() > size;
        List<ProductSummaryResponse> products = rows.stream()
                .limit(size)
                .map(ProductSummaryResponse::from)
                .toList();

        // 마지막 값은 다음 페이지의 시작점이 된다. 만약 조회된 데이터가 없다면 null로 설정한다.
        String nextCursorId = products.isEmpty() ? null : products.getLast().id();

        return new ProductCursorResponse(products, nextCursorId, hasNext);
    }
}
