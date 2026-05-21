package jeong.awsshop.product.service.productread;

/**
 * category 상품 조회에서 지원하는 정렬 방향을 표현한다.
 */
public enum CategoryProductDirection {
    ASC,
    DESC;

    /**
     * 외부 query parameter를 내부 정렬 방향으로 해석한다.
     */
    public static CategoryProductDirection from(String direction) {
        if ("asc".equalsIgnoreCase(direction)) {
            return ASC;
        }
        return DESC;
    }
}
