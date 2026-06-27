package jeong.awsshop.product.service.search.criteria;

public enum ProductSearchSort {
    AVERAGE_RATING("averageRating"),
    RATING_NUMBER("ratingNumber"),
    PRICE("price");

    private final String fieldName;

    ProductSearchSort(String fieldName) {
        this.fieldName = fieldName;
    }

    public static ProductSearchSort from(String sort) {
        if (sort == null || sort.isBlank()) {
            return AVERAGE_RATING;
        }
        if (sort.contains(RATING_NUMBER.fieldName)) {
            return RATING_NUMBER;
        }
        if (sort.contains(PRICE.fieldName)) {
            return PRICE;
        }
        return AVERAGE_RATING;
    }

    public String fieldName() {
        return fieldName;
    }
}
