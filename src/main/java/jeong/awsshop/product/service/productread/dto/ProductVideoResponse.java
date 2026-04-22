package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductVideoDetailProjection;

public record ProductVideoResponse(
        String title,
        String url,
        String userId
) {

    /**
     * video 상세 projection을 응답 DTO로 변환한다.
     */
    public static ProductVideoResponse from(ProductVideoDetailProjection row) {
        return new ProductVideoResponse(row.getTitle(), row.getUrl(), row.getUserId());
    }
}
