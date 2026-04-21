package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;
import jeong.awsshop.product.domain.MainCategory;

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
}
