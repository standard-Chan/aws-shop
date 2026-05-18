package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;

public record CategoryCursor(
        Long id,
        BigDecimal averageRating,
        Integer ratingNumber,
        BigDecimal price
) {

    /**
     * 기존 테스트와 호출부 호환을 위해 price 없는 생성자를 유지한다.
     */
    public CategoryCursor(Long id, BigDecimal averageRating, Integer ratingNumber) {
        this(id, averageRating, ratingNumber, null);
    }

    /**
     * averageRating 정렬 응답에 사용할 cursor를 만든다.
     */
    public static CategoryCursor averageRatingCursor(ProductSummaryResponse product) {
        return new CategoryCursor(product.id(), product.averageRating(), null, null);
    }

    /**
     * ratingNumber 정렬 응답에 사용할 cursor를 만든다.
     */
    public static CategoryCursor ratingNumberCursor(ProductSummaryResponse product) {
        return new CategoryCursor(product.id(), null, product.ratingNumber(), null);
    }

    /**
     * price 정렬 응답에 사용할 cursor를 만든다.
     */
    public static CategoryCursor priceCursor(ProductSummaryResponse product) {
        return new CategoryCursor(product.id(), null, null, product.price());
    }
}
