package jeong.awsshop.product.repository.projection;

import java.math.BigDecimal;

/**
 * Spring Data JPA native query 결과를 alias 기반으로 받기 위한 projection
 *
 * native query는 record 생성자 매핑을 바로 쓰기 어렵기 때문에,
 * SELECT alias와 getter 이름을 맞추는 interface projection을 사용한다.
 * 예: parentAsin alias -> getParentAsin()
 */
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
