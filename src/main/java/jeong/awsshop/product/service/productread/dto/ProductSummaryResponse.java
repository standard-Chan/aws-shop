package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductSummaryResponse(
        Long id,
        String parentAsin,
        String title,
        MainCategory mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        ProductImageResponse image
) {

    /**
     * native projection 한 row를 상품 요약 응답으로 변환한다.
     */
    public static ProductSummaryResponse from(ProductSummaryNativeProjection row) {
        return new ProductSummaryResponse(
                row.getId(),
                row.getParentAsin(),
                row.getTitle(),
                MainCategory.valueOf(row.getMainCategory()),
                row.getAverageRating(),
                row.getRatingNumber(),
                row.getPrice(),
                row.getStore(),
                ProductImageResponse.from(row)
        );
    }
}
