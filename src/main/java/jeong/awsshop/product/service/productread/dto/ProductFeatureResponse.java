package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductFeatureDetailProjection;

public record ProductFeatureResponse(
        Integer featureIndex,
        String feature
) {

    /**
     * feature 상세 projection을 응답 DTO로 변환한다.
     */
    public static ProductFeatureResponse from(ProductFeatureDetailProjection row) {
        return new ProductFeatureResponse(row.getFeatureIndex(), row.getFeature());
    }
}
