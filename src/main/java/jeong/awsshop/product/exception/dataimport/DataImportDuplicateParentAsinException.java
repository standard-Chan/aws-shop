package jeong.awsshop.product.exception.dataimport;

public class DataImportDuplicateParentAsinException extends DataImportException {

    public DataImportDuplicateParentAsinException(String parentAsin) {
        super("[중복 parentAsin 검증 실패]: parentAsin=" + parentAsin + " 이(가) 이미 존재합니다.");
    }
}
