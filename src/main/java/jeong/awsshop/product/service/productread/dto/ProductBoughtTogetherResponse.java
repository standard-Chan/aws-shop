package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductBoughtTogetherDetailProjection;

public record ProductBoughtTogetherResponse(
        String relatedProductId,
        String relatedProductTitle,
        String relatedProductImageUrl
) {

    /**
     * boughtTogether 상세 projection을 응답 DTO로 변환한다.
     */
    public static ProductBoughtTogetherResponse from(ProductBoughtTogetherDetailProjection row) {
        return new ProductBoughtTogetherResponse(
                String.valueOf(row.getRelatedProductId()),
                row.getRelatedProductTitle(),
                row.getRelatedProductImageUrl()
        );
    }
}
