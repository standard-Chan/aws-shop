package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jeong.awsshop.product.exception.dataimport.DataImportParsingException;
import org.springframework.stereotype.Component;

/**
 * JSON line 1건을 트리 형태로 변환하는 파서
 */
@Component
public class DataImportJsonParser {

    private final ObjectMapper objectMapper;

    public DataImportJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 문자열 JSON line을 JsonNode로 변환한다.
     */
    public JsonNode parse(String jsonLine) {
        try {
            return objectMapper.readTree(jsonLine);
        } catch (JsonProcessingException e) {
            throw new DataImportParsingException("[DataImport JSON 파싱 실패]: " + e.getOriginalMessage(), e);
        }
    }
}
