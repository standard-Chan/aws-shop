package jeong.awsshop.product.exception.dataimport;

public class DataImportRequiredFieldException extends RuntimeException {

    public DataImportRequiredFieldException(String message) {
        super(message);
    }
}
