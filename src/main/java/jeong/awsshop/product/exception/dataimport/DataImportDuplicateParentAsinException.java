package jeong.awsshop.product.exception.dataimport;

public class DataImportDuplicateParentAsinException extends RuntimeException {

    public DataImportDuplicateParentAsinException(String parentAsin) {
        super("Duplicate parentAsin: " + parentAsin);
    }
}
