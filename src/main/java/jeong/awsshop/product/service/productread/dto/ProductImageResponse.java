package jeong.awsshop.product.service.productread.dto;

import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.repository.projection.ProductImageDetailProjection;

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

    /**
     * Product 상세 image projection 값을 응답 DTO로 변환한다.
     */
    public static ProductImageResponse from(ProductImageDetailProjection row) {
        return new ProductImageResponse(
                row.getVariant(),
                row.getThumb(),
                row.getLarge(),
                row.getHiRes()
        );
    }
}
