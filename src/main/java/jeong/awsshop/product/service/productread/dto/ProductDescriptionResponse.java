package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductDescriptionDetailProjection;

public record ProductDescriptionResponse(
        Integer descriptionIndex,
        String description
) {

    /**
     * description 상세 projection을 응답 DTO로 변환한다.
     */
    public static ProductDescriptionResponse from(ProductDescriptionDetailProjection row) {
        return new ProductDescriptionResponse(row.getDescriptionIndex(), row.getDescription());
    }
}
