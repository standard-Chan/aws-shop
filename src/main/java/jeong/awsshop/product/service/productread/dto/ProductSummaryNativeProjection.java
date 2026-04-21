package jeong.awsshop.product.service.productread.dto;

import java.math.BigDecimal;

public interface ProductSummaryNativeProjection {

    Long getId();

    String getParentAsin();

    String getTitle();

    String getMainCategory();

    BigDecimal getAverageRating();

    Integer getRatingNumber();

    BigDecimal getPrice();

    String getStore();

    String getImageVariant();

    String getImageThumb();

    String getImageLarge();

    String getImageHiRes();
}
