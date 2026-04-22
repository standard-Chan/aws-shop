package jeong.awsshop.product.repository.projection;

import java.math.BigDecimal;

public interface ProductDetailProjection {

    Long getId();

    String getParentAsin();

    String getTitle();

    String getMainCategory();

    BigDecimal getAverageRating();

    Integer getRatingNumber();

    BigDecimal getPrice();

    String getStore();

    String getDetails();
}
