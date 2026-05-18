package jeong.awsshop.product.service.productread;

import java.math.BigDecimal;
import java.util.List;
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
            String order
    ) {
        String normalizedCategory = normalizeCategory(mainCategory);
        String selectedSort = selectSort(sort);
        String selectedOrder = selectOrder(order);
        ProductDetailProjection cursorProduct = findCursorProduct(normalizedCategory, cursorId);

        validateCursor(
                cursorProduct,
                selectedSort
        );

        List<ProductSummaryNativeProjection> rows = findCategoryProductSummaries(
                normalizedCategory,
                cursorId,
                cursorProduct,
                selectedSort,
                selectedOrder,
                queryLimitForHasNext(size)
        );

        return ProductCategoryCursorResponse.from(rows, size, selectedSort, selectedOrder);
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
     * sort / order 조합에 따라 필요한 category 조회 쿼리를 호출한다.
     */
    private List<ProductSummaryNativeProjection> findCategoryProductSummaries(
            String mainCategory,
            Long cursorId,
            ProductDetailProjection cursorProduct,
            String sort,
            String order,
            int limit
    ) {
        if ("ratingNumber".equals(sort)) {
            return productRepository.findCategoryProductSummariesOrderByRatingNumber(
                    mainCategory,
                    cursorId,
                    cursorProduct == null ? null : cursorProduct.getRatingNumber(),
                    limit
            );
        }
        if ("price".equals(sort)) {
            if ("asc".equals(order)) {
                return productRepository.findCategoryProductSummariesOrderByPriceAsc(
                        mainCategory,
                        cursorId,
                        cursorProduct == null ? null : cursorProduct.getPrice(),
                        limit
                );
            }
            return productRepository.findCategoryProductSummariesOrderByPriceDesc(
                    mainCategory,
                    cursorId,
                    cursorProduct == null ? null : cursorProduct.getPrice(),
                    limit
            );
        }
        return productRepository.findCategoryProductSummariesOrderByAverageRating(
                mainCategory,
                cursorId,
                cursorProduct == null ? null : cursorProduct.getAverageRating(),
                limit
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
    private void validateCursor(
            ProductDetailProjection cursorProduct,
            String sort
    ) {
        if (cursorProduct == null) {
            return;
        }
        if ("ratingNumber".equals(sort) && cursorProduct.getRatingNumber() == null) {
            throw new MissingCategorySortCursorException();
        }
        if ("price".equals(sort) && cursorProduct.getPrice() == null) {
            throw new MissingCategorySortCursorException();
        }
        if ("averageRating".equals(sort) && cursorProduct.getAverageRating() == null) {
            throw new MissingCategorySortCursorException();
        }
    }

    /**
     * cursor 상품을 조회하고 category 일치 여부를 검증한다.
     */
    private ProductDetailProjection findCursorProduct(String mainCategory, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        ProductDetailProjection cursorProduct = productRepository.findDetailById(cursorId)
                .orElseThrow(ProductCategoryCursorNotFoundException::new);
        if (!mainCategory.equals(cursorProduct.getMainCategory())) {
            throw new ProductCategoryCursorMismatchException();
        }
        return cursorProduct;
    }

    /**
     * 우선순위 규칙에 맞게 실제 정렬 기준을 확정한다.
     */
    private String selectSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "averageRating";
        }
        if (sort.contains("ratingNumber")) {
            return "ratingNumber";
        }
        if (sort.contains("averageRating")) {
            return "averageRating";
        }
        if (sort.contains("price")) {
            return "price";
        }
        return "averageRating";
    }

    /**
     * order가 desc가 아니면 asc를 기본값으로 사용한다.
     */
    private String selectOrder(String order) {
        if ("desc".equalsIgnoreCase(order)) {
            return "desc";
        }
        return "asc";
    }
}
