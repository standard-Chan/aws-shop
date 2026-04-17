package jeong.awsshop.product.exception.dataimport;

public class DataImportRequiredFieldException extends DataImportException {

    public DataImportRequiredFieldException(String fieldName) {
        super("[상품 생성 실패]: " + fieldName + "이(가) 누락되었습니다.");
    }
}
