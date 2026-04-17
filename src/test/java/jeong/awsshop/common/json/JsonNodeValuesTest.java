package jeong.awsshop.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonNodeValuesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("JsonNode 값은 문자열과 기본값 규칙에 맞게 추출되어야 한다")
    void should_extract_values_using_common_rules() throws Exception {
        // Given: 문자열, 숫자, 객체를 포함한 JSON
        JsonNode rootNode = objectMapper.readTree("""
            {
              "text": "hello",
              "decimal": 4.5,
              "integer": 12,
              "longValue": 1234567890123,
              "details": {"color":"red","size":"M"}
            }
            """);

        // When: 공용 추출 함수를 호출한다
        String text = JsonNodeValues.text(rootNode, "text");
        BigDecimal decimal = JsonNodeValues.decimal(rootNode.get("decimal"));
        Integer integer = JsonNodeValues.integer(rootNode.get("integer"));
        Long longValue = JsonNodeValues.longValue(rootNode.get("longValue"));
        String details = JsonNodeValues.details(rootNode.get("details"));

        // Then: 각 값이 기대 규칙대로 반환되어야 한다
        assertThat(text).isEqualTo("hello");
        assertThat(decimal).isEqualByComparingTo("4.5");
        assertThat(integer).isEqualTo(12);
        assertThat(longValue).isEqualTo(1234567890123L);
        assertThat(details).isEqualTo("{\"color\":\"red\",\"size\":\"M\"}");
    }

    @Test
    @DisplayName("null JsonNode는 안전한 기본값으로 처리되어야 한다")
    void should_return_safe_defaults_when_node_is_null() {
        // Given: null 값

        // When: 공용 추출 함수를 호출한다
        String text = JsonNodeValues.text((JsonNode) null);
        String fieldText = JsonNodeValues.text((JsonNode) null, "missing");
        boolean blank = JsonNodeValues.isBlank(null);
        BigDecimal decimal = JsonNodeValues.decimal(null);
        Integer integer = JsonNodeValues.integer(null);
        Long longValue = JsonNodeValues.longValue(null);
        String details = JsonNodeValues.details(null);

        // Then: null-safe 기본값이 반환되어야 한다
        assertThat(text).isNull();
        assertThat(fieldText).isNull();
        assertThat(blank).isTrue();
        assertThat(decimal).isNull();
        assertThat(integer).isEqualTo(0);
        assertThat(longValue).isNull();
        assertThat(details).isNull();
    }

    @Test
    @DisplayName("blank 문자열은 blank로 판단되어야 한다")
    void should_recognize_blank_string() {
        // Given: 공백 문자열

        // When: blank 판별 함수를 호출한다
        boolean blank = JsonNodeValues.isBlank("   ");

        // Then: blank로 판단되어야 한다
        assertThat(blank).isTrue();
    }

    @Test
    @DisplayName("숫자 파싱 실패 시 안전한 기본값이 반환되어야 한다")
    void should_return_safe_defaults_when_numeric_parsing_fails() throws Exception {
        // Given: 숫자로 해석하기 어려운 JSON
        JsonNode rootNode = objectMapper.readTree("""
            {
              "decimal": "not-a-number",
              "integer": "not-a-number",
              "longValue": "not-a-number"
            }
            """);

        // When: 숫자 추출 함수를 호출한다
        BigDecimal decimal = JsonNodeValues.decimal(rootNode.get("decimal"));
        Integer integer = JsonNodeValues.integer(rootNode.get("integer"));
        Long longValue = JsonNodeValues.longValue(rootNode.get("longValue"));

        // Then: 실패해도 안전한 기본값이 반환되어야 한다
        assertThat(decimal).isEqualByComparingTo("0");
        assertThat(integer).isEqualTo(0);
        assertThat(longValue).isEqualTo(0L);
    }
}
