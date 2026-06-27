package jeong.awsshop.product.service.search.dto;

import jeong.awsshop.product.service.search.document.ProductSearchImageDocument;

public record ProductSearchImageResponse(
        String variant,
        String thumb,
        String large,
        String hiRes
) {

    public static ProductSearchImageResponse from(ProductSearchImageDocument image) {
        if (image == null) {
            return null;
        }
        return new ProductSearchImageResponse(
                image.variant(),
                image.thumb(),
                image.large(),
                image.hiRes()
        );
    }
}
