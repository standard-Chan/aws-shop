package jeong.awsshop.product.service.productread.dto;

import java.util.List;

public record ProductCursorResponse(
        List<ProductSummaryResponse> products,
        Long nextCursorId,
        boolean hasNext
) {
}
