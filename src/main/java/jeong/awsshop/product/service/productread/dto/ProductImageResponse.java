package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductImageResponse(
        String variant,
        String thumb,
        String large,
        String hiRes
) {

    /**
     * Product summary의 projection 값을 응답 DTO로 변환한다.
     */
    public static ProductImageResponse from(ProductSummaryNativeProjection row) {
        if (row.getImageVariant() == null
                && row.getImageThumb() == null
                && row.getImageLarge() == null
                && row.getImageHiRes() == null) {
            return null;
        }

        return new ProductImageResponse(
                row.getImageVariant(),
                row.getImageThumb(),
                row.getImageLarge(),
                row.getImageHiRes()
        );
    }
}
