package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.domain.MainCategory;

public record ProductDetailResponse(
        Long id,
        String parentAsin,
        String title,
        MainCategory mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        Map<String, Object> details,
        List<ProductFeatureResponse> features,
        List<ProductDescriptionResponse> descriptions,
        List<ProductCategoryResponse> categories,
        List<ProductBoughtTogetherResponse> boughtTogether,
        List<ProductImageResponse> images,
        List<ProductVideoResponse> videos
) {
}
