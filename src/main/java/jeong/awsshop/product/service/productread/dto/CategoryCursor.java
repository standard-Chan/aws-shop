package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;

public record CategoryCursor(
        Long id,
        BigDecimal averageRating,
        Integer ratingNumber
) {

    /**
     * averageRating 정렬 응답에 사용할 cursor를 만든다.
     */
    public static CategoryCursor averageRatingCursor(ProductSummaryResponse product) {
        return new CategoryCursor(product.id(), product.averageRating(), null);
    }

    /**
     * ratingNumber 정렬 응답에 사용할 cursor를 만든다.
     */
    public static CategoryCursor ratingNumberCursor(ProductSummaryResponse product) {
        return new CategoryCursor(product.id(), null, product.ratingNumber());
    }
}
