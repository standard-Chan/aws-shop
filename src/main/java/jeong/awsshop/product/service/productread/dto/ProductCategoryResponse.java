package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductCategoryDetailProjection;

public record ProductCategoryResponse(
        String category
) {

    /**
     * category 상세 projection을 응답 DTO로 변환한다.
     */
    public static ProductCategoryResponse from(ProductCategoryDetailProjection row) {
        return new ProductCategoryResponse(row.getCategory());
    }
}
