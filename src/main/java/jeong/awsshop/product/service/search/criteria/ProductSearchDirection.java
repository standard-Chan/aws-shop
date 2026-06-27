package jeong.awsshop.product.service.search.criteria;

public enum ProductSearchDirection {
    ASC,
    DESC;

    public static ProductSearchDirection from(String order) {
        if ("asc".equalsIgnoreCase(order)) {
            return ASC;
        }
        return DESC;
    }
}
