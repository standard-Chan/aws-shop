package jeong.awsshop.product.exception.dataimport;

public class DataImportPersistenceException extends DataImportException {

    public DataImportPersistenceException(String message, Throwable cause) {
        super("[DataImport 저장 실패]: " + message, cause);
    }
}
