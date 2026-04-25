package jeong.awsshop.product.service.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainCategoryTest {

    @Test
    @DisplayName("category 문자열은 정규화된 대문자 underscore 형식으로 변환해야 한다")
    void should_normalize_category_to_uppercase_underscore_format() {
        assertThat(MainCategoryNormalizer.normalize("Gift Cards")).isEqualTo("GIFT_CARDS");
        assertThat(MainCategoryNormalizer.normalize("Gift-Cards")).isEqualTo("GIFT_CARDS");
        assertThat(MainCategoryNormalizer.normalize("Unexpected Category")).isEqualTo("UNEXPECTED_CATEGORY");
    }
}
