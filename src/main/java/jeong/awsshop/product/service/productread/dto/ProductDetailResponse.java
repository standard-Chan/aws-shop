package jeong.awsshop.product.service.productread.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.repository.projection.ProductBoughtTogetherDetailProjection;
import jeong.awsshop.product.repository.projection.ProductCategoryDetailProjection;
import jeong.awsshop.product.repository.projection.ProductDescriptionDetailProjection;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductFeatureDetailProjection;
import jeong.awsshop.product.repository.projection.ProductImageDetailProjection;
import jeong.awsshop.product.repository.projection.ProductVideoDetailProjection;

public record ProductDetailResponse(
        String id,
        String parentAsin,
        String title,
        String mainCategory,
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Product 상세 projection 묶음을 상세 응답 DTO로 조립한다.
     */
    public static ProductDetailResponse from(
            ProductDetailProjection product,
            List<ProductFeatureDetailProjection> features,
            List<ProductDescriptionDetailProjection> descriptions,
            List<ProductCategoryDetailProjection> categories,
            List<ProductBoughtTogetherDetailProjection> boughtTogether,
            List<ProductImageDetailProjection> images,
            List<ProductVideoDetailProjection> videos
    ) {
        return new ProductDetailResponse(
                String.valueOf(product.getId()),
                product.getParentAsin(),
                product.getTitle(),
                product.getMainCategory(),
                product.getAverageRating(),
                product.getRatingNumber(),
                product.getPrice(),
                product.getStore(),
                parseDetailsJson(product.getDetails()),
                features.stream()
                        .map(ProductFeatureResponse::from)
                        .toList(),
                descriptions.stream()
                        .map(ProductDescriptionResponse::from)
                        .toList(),
                categories.stream()
                        .map(ProductCategoryResponse::from)
                        .toList(),
                boughtTogether.stream()
                        .map(ProductBoughtTogetherResponse::from)
                        .toList(),
                images.stream()
                        .map(ProductImageResponse::from)
                        .toList(),
                videos.stream()
                        .map(ProductVideoResponse::from)
                        .toList()
        );
    }

    /**
     * details JSON 문자열을 응답용 map으로 변환한다.
     */
    private static Map<String, Object> parseDetailsJson(String details) {
        if (details == null || details.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(details, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("[Product 상세 조회 실패]: details JSON 파싱에 실패했습니다.", e);
        }
    }
}
