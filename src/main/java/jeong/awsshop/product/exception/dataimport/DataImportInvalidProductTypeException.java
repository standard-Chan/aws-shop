package jeong.awsshop.product.exception.dataimport;

import com.fasterxml.jackson.databind.JsonNode;

public class DataImportInvalidProductTypeException extends DataImportException {

    public DataImportInvalidProductTypeException(String fieldName, JsonNode actualValue) {
        super("[상품 생성 실패]: " + fieldName + "은(는) 숫자 타입이어야 합니다. actualValue=" + actualValue);
    }
}
