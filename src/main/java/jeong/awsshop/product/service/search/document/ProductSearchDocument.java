package jeong.awsshop.product.service.search.document;

import java.math.BigDecimal;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;

public record ProductSearchDocument(
        Long id,
        String parentAsin,
        String title,
        String mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        ProductSearchImageDocument image
) {

    public static ProductSearchDocument from(ProductSummaryNativeProjection row) {
        ProductSearchImageDocument image = null;
        if (row.getImageVariant() != null || row.getImageThumb() != null
                || row.getImageLarge() != null || row.getImageHiRes() != null) {
            image = new ProductSearchImageDocument(
                    row.getImageVariant(),
                    row.getImageThumb(),
                    row.getImageLarge(),
                    row.getImageHiRes()
            );
        }
        return new ProductSearchDocument(
                row.getId(),
                row.getParentAsin(),
                row.getTitle(),
                row.getMainCategory(),
                row.getAverageRating(),
                row.getRatingNumber(),
                row.getPrice(),
                row.getStore(),
                image
        );
    }
}
