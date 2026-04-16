package jeong.awsshop.product.service.dataimport;

public enum DataImportJsonKey {
    PARENT_ASIN("parent_asin"),
    TITLE("title"),
    MAIN_CATEGORY("main_category"),
    AVERAGE_RATING("average_rating"),
    RATING_NUMBER("rating_number"),
    PRICE("price"),
    STORE("store"),
    DETAILS("details"),
    FEATURES("features"),
    DESCRIPTION("description"),
    CATEGORIES("categories"),
    IMAGES("images"),
    VIDEOS("videos"),
    BOUGHT_TOGETHER("bought_together"),
    RELATED_PRODUCT_ID("relatedProductId"),
    RELATED_PRODUCT_ID_SNAKE("related_product_id"),
    RELATED_PRODUCT_TITLE("relatedProductTitle"),
    RELATED_PRODUCT_IMAGE_URL("relatedProductImageUrl");

    private final String value;

    DataImportJsonKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
