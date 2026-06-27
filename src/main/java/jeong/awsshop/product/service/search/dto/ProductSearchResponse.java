package jeong.awsshop.product.service.search.dto;

import java.util.List;

public record ProductSearchResponse(
        List<ProductSearchItemResponse> products,
        String nextCursor,
        boolean hasNext
) {

    public static ProductSearchResponse empty() {
        return new ProductSearchResponse(List.of(), null, false);
    }
}
