package jeong.awsshop.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MainCategoryTest {

    @Test
    @DisplayName("query param으로 받은 '-' category 값을 MainCategory로 변환해야 한다")
    void should_convert_hyphenated_category_when_category_is_query_param_value() {
        // Given: query parameter에서 공백 대신 '-'로 전달된 category 값
        String category = "Gift-Cards";

        // When: MainCategory 변환 메서드로 category를 변환한다
        MainCategory result = MainCategory.fromQueryParam(category);

        // Then: enum name에 맞는 MainCategory로 변환되어야 한다
        assertThat(result).isEqualTo(MainCategory.GIFT_CARDS);
    }
}
