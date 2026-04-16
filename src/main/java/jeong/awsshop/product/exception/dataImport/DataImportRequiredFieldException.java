package jeong.awsshop.product.exception.dataImport;

public class DataImportRequiredFieldException extends RuntimeException {

    public DataImportRequiredFieldException(String message) {
        super(message);
    }
}
