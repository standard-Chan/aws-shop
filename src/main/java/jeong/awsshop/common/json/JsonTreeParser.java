package jeong.awsshop.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * JSON 문자열을 JsonNode 트리로 파싱하는 공용 컴포넌트다.
 */
@Component
public class JsonTreeParser {

    private final ObjectMapper objectMapper;

    public JsonTreeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 문자열 JSON을 트리로 변환한다.
     */
    public JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JsonParsingException("JSON parsing failed: " + e.getOriginalMessage(), e);
        }
    }
}
