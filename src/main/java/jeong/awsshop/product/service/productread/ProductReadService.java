package jeong.awsshop.product.service.productread;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import jeong.awsshop.product.service.dataimport.MainCategoryNormalizer;
import jeong.awsshop.product.exception.productread.MissingCategorySortCursorException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorMismatchException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorNotFoundException;
import jeong.awsshop.product.exception.productread.ProductNotFoundException;
import jeong.awsshop.product.repository.ProductBoughtTogetherRepository;
import jeong.awsshop.product.repository.ProductCategoryRepository;
import jeong.awsshop.product.repository.ProductDescriptionRepository;
import jeong.awsshop.product.repository.ProductFeatureRepository;
import jeong.awsshop.product.repository.ProductImageRepository;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.ProductVideoRepository;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductRepository productRepository;
    private final ProductFeatureRepository productFeatureRepository;
    private final ProductDescriptionRepository productDescriptionRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductBoughtTogetherRepository productBoughtTogetherRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVideoRepository productVideoRepository;

    /**
     * id 조회 cursor 목록 조회 결과를 응답 DTO로 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductCursorResponse getProducts(int size, Long cursorId) {
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(cursorId, queryLimitForHasNext(size));
        return ProductCursorResponse.from(rows, size);
    }

    /**
     * category별 Product cursor 목록 조회 결과를 응답 DTO로 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductCategoryCursorResponse getProductsByCategory(
            String mainCategory,
            int size,
            Long cursorId,
            String sort,
            String direction
    ) {
        String normalizedCategory = normalizeCategory(mainCategory);
        CategoryProductSort selectedSort = resolveProductSort(sort);
        CategoryProductDirection selectedDirection = resolveProductDirection(direction);
        ProductDetailProjection cursorProduct = prepareCategoryCursorProduct(
                normalizedCategory,
                cursorId,
                selectedSort
        );

        return ProductCategoryCursorResponse.from(
                readCategoryProductSummaries(
                        normalizedCategory,
                        size,
                        cursorId,
                        cursorProduct,
                        selectedSort,
                        selectedDirection
                ),
                size,
                selectedSort,
                selectedDirection
        );
    }

    /**
     * keyword별 Product cursor 목록 조회 결과를 응답 DTO로 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductCategoryCursorResponse getProductsByKeyword(
            String keyword,
            int size,
            Long cursorId,
            String sort,
            String direction
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (isBlankKeyword(normalizedKeyword)) {
            return emptyCategoryCursorResponse();
        }

        CategoryProductSort selectedSort = resolveProductSort(sort);
        CategoryProductDirection selectedDirection = resolveProductDirection(direction);
        ProductDetailProjection cursorProduct = prepareKeywordCursorProduct(
                normalizedKeyword,
                cursorId,
                selectedSort
        );

        return ProductCategoryCursorResponse.from(
                readKeywordProductSummaries(
                        normalizedKeyword,
                        size,
                        cursorId,
                        cursorProduct,
                        selectedSort,
                        selectedDirection
                ),
                size,
                selectedSort,
                selectedDirection
        );
    }

    /**
     * Product id로 상세 조회 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long id) {
        ProductDetailProjection product = productRepository.findDetailById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // 단일 쿼리가 아닌, 개별 쿼리로 연관 정보들을 각각 조회한다. (단일 JOIN 시, 지나치게 많은 rows 조회 문제)
        return ProductDetailResponse.from(
                product,
                productFeatureRepository.findFeatureDetailsByProductId(id),
                productDescriptionRepository.findDescriptionDetailsByProductId(id),
                productCategoryRepository.findCategoryDetailsByProductId(id),
                productBoughtTogetherRepository.findBoughtTogetherDetailsByProductId(id),
                productImageRepository.findImageDetailsByProductId(id),
                productVideoRepository.findVideoDetailsByProductId(id)
        );
    }

    /**
     * sort / direction 조합에 따라 필요한 category 조회 쿼리를 호출한다.
     */
    private List<ProductSummaryNativeProjection> findCategoryProductSummaries(
            String mainCategory,
            Long cursorId,
            ProductDetailProjection cursorProduct,
            CategoryProductSort sort,
            CategoryProductDirection direction,
            int limit
    ) {
        if (sort == CategoryProductSort.RATING_NUMBER) {
            return productRepository.findCategoryProductSummariesOrderByRatingNumber(
                    mainCategory,
                    cursorId,
                    ratingNumberOf(cursorProduct),
                    limit
            );
        }
        if (sort == CategoryProductSort.PRICE) {
            if (direction == CategoryProductDirection.ASC) {
                return productRepository.findCategoryProductSummariesOrderByPriceAsc(
                        mainCategory,
                        cursorId,
                        priceOf(cursorProduct),
                        limit
                );
            }
            return productRepository.findCategoryProductSummariesOrderByPriceDesc(
                    mainCategory,
                    cursorId,
                    priceOf(cursorProduct),
                    limit
            );
        }
        return productRepository.findCategoryProductSummariesOrderByAverageRating(
                mainCategory,
                cursorId,
                averageRatingOf(cursorProduct),
                limit
        );
    }

    /**
     * sort / direction 조합에 따라 필요한 keyword 조회 쿼리를 호출한다.
     */
    private List<ProductSummaryNativeProjection> findKeywordProductSummaries(
            String keyword,
            Long cursorId,
            ProductDetailProjection cursorProduct,
            CategoryProductSort sort,
            CategoryProductDirection direction,
            int limit
    ) {
        if (sort == CategoryProductSort.RATING_NUMBER) {
            return productRepository.findKeywordProductSummariesOrderByRatingNumber(
                    keyword,
                    cursorId,
                    ratingNumberOf(cursorProduct),
                    limit
            );
        }
        if (sort == CategoryProductSort.PRICE) {
            if (direction == CategoryProductDirection.ASC) {
                return productRepository.findKeywordProductSummariesOrderByPriceAsc(
                        keyword,
                        cursorId,
                        priceOf(cursorProduct),
                        limit
                );
            }
            return productRepository.findKeywordProductSummariesOrderByPriceDesc(
                    keyword,
                    cursorId,
                    priceOf(cursorProduct),
                    limit
            );
        }
        return productRepository.findKeywordProductSummariesOrderByAverageRating(
                keyword,
                cursorId,
                averageRatingOf(cursorProduct),
                limit
        );
    }

    private boolean isBlankKeyword(String keyword) {
        return keyword.isBlank();
    }

    private ProductCategoryCursorResponse emptyCategoryCursorResponse() {
        return new ProductCategoryCursorResponse(List.of(), null, false);
    }

    private CategoryProductSort resolveProductSort(String sort) {
        return CategoryProductSort.from(sort);
    }

    private CategoryProductDirection resolveProductDirection(String direction) {
        return CategoryProductDirection.from(direction);
    }

    private ProductDetailProjection prepareKeywordCursorProduct(
            String normalizedKeyword,
            Long cursorId,
            CategoryProductSort selectedSort
    ) {
        return prepareCursorProduct(
                cursorId,
                cursorProduct -> containsKeyword(cursorProduct.getTitle(), normalizedKeyword),
                selectedSort
        );
    }

    private ProductDetailProjection prepareCategoryCursorProduct(
            String normalizedCategory,
            Long cursorId,
            CategoryProductSort selectedSort
    ) {
        return prepareCursorProduct(
                cursorId,
                cursorProduct -> normalizedCategory.equals(cursorProduct.getMainCategory()),
                selectedSort
        );
    }

    /**
     * cursor 상품 조회, 요청 집합 일치 검증, 정렬값 검증을 한 흐름으로 묶는다.
     */
    private ProductDetailProjection prepareCursorProduct(
            Long cursorId,
            Predicate<ProductDetailProjection> matchesRequestedSet,
            CategoryProductSort selectedSort
    ) {
        if (cursorId == null) {
            return null;
        }

        ProductDetailProjection cursorProduct = productRepository.findDetailById(cursorId)
                .orElseThrow(ProductCategoryCursorNotFoundException::new);

        validateCursorBelongsToRequestedSet(cursorProduct, matchesRequestedSet);
        validateCursorSortValue(cursorProduct, selectedSort);
        return cursorProduct;
    }

    private List<ProductSummaryNativeProjection> readKeywordProductSummaries(
            String normalizedKeyword,
            int size,
            Long cursorId,
            ProductDetailProjection cursorProduct,
            CategoryProductSort selectedSort,
            CategoryProductDirection selectedDirection
    ) {
        // LIKE 검색에 필요한 escape 패턴과 기존 cursor 규칙을 함께 사용한다.
        return findKeywordProductSummaries(
                normalizedKeyword,
                cursorId,
                cursorProduct,
                selectedSort,
                selectedDirection,
                queryLimitForHasNext(size)
        );
    }

    private List<ProductSummaryNativeProjection> readCategoryProductSummaries(
            String normalizedCategory,
            int size,
            Long cursorId,
            ProductDetailProjection cursorProduct,
            CategoryProductSort selectedSort,
            CategoryProductDirection selectedDirection
    ) {
        return findCategoryProductSummaries(
                normalizedCategory,
                cursorId,
                cursorProduct,
                selectedSort,
                selectedDirection,
                queryLimitForHasNext(size)
        );
    }

    /**
     * 다음 페이지 존재 여부를 확인하기 위해 요청 size보다 1개 더 조회한다.
     */
    private int queryLimitForHasNext(int size) {
        return size + 1;
    }

    /**
     * query parameter의 category를 저장 포맷과 같은 문자열로 정규화한다.
     */
    private String normalizeCategory(String mainCategory) {
        return MainCategoryNormalizer.normalize(mainCategory);
    }

    /**
     * sort 문자열 기준으로 필요한 cursor 조합과 cursor 상품 일치 여부를 검증한다.
     */
    private void validateCursorSortValue(
            ProductDetailProjection cursorProduct,
            CategoryProductSort sort
    ) {
        if (cursorProduct == null) {
            return;
        }
        if (sort == CategoryProductSort.RATING_NUMBER && cursorProduct.getRatingNumber() == null) {
            throw new MissingCategorySortCursorException();
        }
        if (sort == CategoryProductSort.PRICE && cursorProduct.getPrice() == null) {
            throw new MissingCategorySortCursorException();
        }
        if (sort == CategoryProductSort.AVERAGE_RATING && cursorProduct.getAverageRating() == null) {
            throw new MissingCategorySortCursorException();
        }
    }

    /**
     * category / keyword 요청 집합에 cursor 상품이 속하는지 검증한다.
     */
    private void validateCursorBelongsToRequestedSet(
            ProductDetailProjection cursorProduct,
            Predicate<ProductDetailProjection> matchesRequestedSet
    ) {
        if (!matchesRequestedSet.test(cursorProduct)) {
            throw new ProductCategoryCursorMismatchException();
        }
    }

    /**
     * keyword 검색 전 입력 문자열의 앞뒤 공백만 제거한다.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return keyword.trim();
    }

    /**
     * 대소문자 무시 contains 규칙으로 cursor 상품의 검색 집합 포함 여부를 판단한다.
     */
    private boolean containsKeyword(String title, String keyword) {
        return title != null && title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private Integer ratingNumberOf(ProductDetailProjection cursorProduct) {
        return cursorProduct == null ? null : cursorProduct.getRatingNumber();
    }

    private BigDecimal priceOf(ProductDetailProjection cursorProduct) {
        return cursorProduct == null ? null : cursorProduct.getPrice();
    }

    private BigDecimal averageRatingOf(ProductDetailProjection cursorProduct) {
        return cursorProduct == null ? null : cursorProduct.getAverageRating();
    }

}
