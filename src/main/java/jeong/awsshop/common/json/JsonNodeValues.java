package jeong.awsshop.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * JsonNode에서 자주 쓰는 값 추출과 기본값 처리를 담당하는 공용 유틸이다.
 */
public final class JsonNodeValues {

    private JsonNodeValues() {
    }

    public static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    public static String text(JsonNode rootNode, String fieldName) {
        return rootNode == null ? null : text(rootNode.get(fieldName));
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.decimalValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        try {
            return Math.max(node.intValue(), 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.longValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String details(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}
