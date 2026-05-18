package jeong.awsshop.product.service.productread;

/**
 * category 상품 조회에서 지원하는 정렬 기준을 표현한다.
 */
public enum CategoryProductSort {
    AVERAGE_RATING("averageRating"),
    RATING_NUMBER("ratingNumber"),
    PRICE("price");

    private final String parameterValue;

    CategoryProductSort(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    /**
     * 외부 query parameter를 내부 정렬 기준으로 해석한다.
     */
    public static CategoryProductSort from(String sort) {
        if (sort == null || sort.isBlank()) {
            return AVERAGE_RATING;
        }
        if (sort.contains(RATING_NUMBER.parameterValue)) {
            return RATING_NUMBER;
        }
        if (sort.contains(AVERAGE_RATING.parameterValue)) {
            return AVERAGE_RATING;
        }
        if (sort.contains(PRICE.parameterValue)) {
            return PRICE;
        }
        return AVERAGE_RATING;
    }
}
