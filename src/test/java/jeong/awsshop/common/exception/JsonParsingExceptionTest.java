package jeong.awsshop.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jeong.awsshop.common.json.JsonTreeParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonParsingExceptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonTreeParser jsonTreeParser = new JsonTreeParser(objectMapper);

    @Test
    @DisplayName("잘못된 JSON은 공용 JSON 파싱 예외로 감싸져야 한다")
    void should_wrap_invalid_json_into_common_parsing_exception() {
        // Given: 잘못된 JSON 문자열
        String invalidJson = "{\"broken\":";

        // When / Then: 공용 예외가 발생해야 하고 prefix를 포함해야 한다
        assertThatThrownBy(() -> jsonTreeParser.parse(invalidJson))
                .isInstanceOf(JsonParsingException.class)
                .hasMessageContaining("[JSON 파싱 실패]:");
    }

    @Test
    @DisplayName("정상 JSON은 파싱되어 JsonNode로 반환되어야 한다")
    void should_parse_valid_json_into_json_node() throws Exception {
        // Given: 정상 JSON 문자열
        String json = "{\"name\":\"product\"}";

        // When: 파서로 읽는다
        JsonNode node = jsonTreeParser.parse(json);

        // Then: JsonNode가 반환되어야 한다
        assertThat(node.get("name").asText()).isEqualTo("product");
    }
}
