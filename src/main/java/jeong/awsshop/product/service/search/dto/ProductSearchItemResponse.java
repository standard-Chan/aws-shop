package jeong.awsshop.product.service.search.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.service.search.document.ProductSearchDocument;

public record ProductSearchItemResponse(
        String id,
        String parentAsin,
        String title,
        String mainCategory,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price,
        String store,
        ProductSearchImageResponse image,
        Double score,
        Map<String, List<String>> highlight
) {

    public static ProductSearchItemResponse from(
            ProductSearchDocument document,
            Double score,
            Map<String, List<String>> highlight
    ) {
        return new ProductSearchItemResponse(
                String.valueOf(document.id()),
                document.parentAsin(),
                document.title(),
                document.mainCategory(),
                document.averageRating(),
                document.ratingNumber(),
                document.price(),
                document.store(),
                ProductSearchImageResponse.from(document.image()),
                score,
                highlight
        );
    }
}
