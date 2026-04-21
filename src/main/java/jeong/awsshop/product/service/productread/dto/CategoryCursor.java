package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;

public record CategoryCursor(
        Long id,
        BigDecimal averageRating,
        Integer ratingNumber
) {
}
