package jeong.awsshop.product.exception.dataImport;

public class DataImportDuplicateParentAsinException extends RuntimeException {

    public DataImportDuplicateParentAsinException(String parentAsin) {
        super("Duplicate parentAsin: " + parentAsin);
    }
}
