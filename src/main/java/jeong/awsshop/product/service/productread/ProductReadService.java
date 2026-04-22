package jeong.awsshop.product.service.productread;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.exception.productread.MissingCategoryCursorIdException;
import jeong.awsshop.product.exception.productread.MissingCategorySortCursorException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorMismatchException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorNotFoundException;
import jeong.awsshop.product.exception.productread.ProductNotFoundException;
import jeong.awsshop.product.exception.productread.UnknownProductCategoryException;
import jeong.awsshop.product.repository.ProductBoughtTogetherRepository;
import jeong.awsshop.product.repository.ProductCategoryRepository;
import jeong.awsshop.product.repository.ProductDescriptionRepository;
import jeong.awsshop.product.repository.ProductFeatureRepository;
import jeong.awsshop.product.repository.ProductImageRepository;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.ProductVideoRepository;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductBoughtTogetherResponse;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductCategoryResponse;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductDescriptionResponse;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import jeong.awsshop.product.service.productread.dto.ProductFeatureResponse;
import jeong.awsshop.product.service.productread.dto.ProductImageResponse;
import jeong.awsshop.product.service.productread.dto.ProductVideoResponse;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * cursor 목록 조회 결과를 응답 DTO로 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductCursorResponse getProducts(int size, Long cursor) {
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(cursor, queryLimitForHasNext(size));
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
            BigDecimal cursorAverageRating,
            Integer cursorRatingNumber,
            boolean averageRating,
            boolean ratingNumber
    ) {
        MainCategory category = parseCategory(mainCategory);
        // averageRating 정렬인지, ratingNumber 정렬인지에 따라 정렬 기준을 선택한다. (averageRating 우선)
        boolean averageRatingSort = shouldSortByAverageRating(averageRating, ratingNumber);
        validateCursor(category, cursorId, cursorAverageRating, cursorRatingNumber, averageRatingSort);

        List<ProductSummaryNativeProjection> rows = findCategoryProductSummaries(
                category,
                cursorId,
                cursorAverageRating,
                cursorRatingNumber,
                averageRatingSort,
                queryLimitForHasNext(size)
        );

        return ProductCategoryCursorResponse.from(rows, size, averageRatingSort);
    }

    /**
     * Product id로 상세 조회 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long id) {
        ProductDetailProjection product = productRepository.findDetailById(id)
                .orElseThrow(ProductNotFoundException::new);

        // 조회 성능을 위해서, 개별 repository에서 필요한 연관 정보들을 각각 조회한다.
        return new ProductDetailResponse(
                product.getId(),
                product.getParentAsin(),
                product.getTitle(),
                MainCategory.valueOf(product.getMainCategory()),
                product.getAverageRating(),
                product.getRatingNumber(),
                product.getPrice(),
                product.getStore(),
                parseDetails(product.getDetails()),
                productFeatureRepository.findFeatureDetailsByProductId(id)
                        .stream()
                        .map(ProductFeatureResponse::from)
                        .toList(),
                productDescriptionRepository.findDescriptionDetailsByProductId(id)
                        .stream()
                        .map(ProductDescriptionResponse::from)
                        .toList(),
                productCategoryRepository.findCategoryDetailsByProductId(id)
                        .stream()
                        .map(ProductCategoryResponse::from)
                        .toList(),
                productBoughtTogetherRepository.findBoughtTogetherDetailsByProductId(id)
                        .stream()
                        .map(ProductBoughtTogetherResponse::from)
                        .toList(),
                productImageRepository.findImageDetailsByProductId(id)
                        .stream()
                        .map(ProductImageResponse::from)
                        .toList(),
                productVideoRepository.findVideoDetailsByProductId(id)
                        .stream()
                        .map(ProductVideoResponse::from)
                        .toList()
        );
    }

    /**
     * details JSON 문자열을 응답용 map으로 변환한다.
     */
    private Map<String, Object> parseDetails(String details) {
        if (details == null || details.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(details, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("[Product 상세 조회 실패]: details JSON 파싱에 실패했습니다.", e);
        }
    }

    /**
     * averageRating 요청이 있거나 정렬 옵션이 없으면 averageRating 정렬을 선택한다.
     */
    private boolean shouldSortByAverageRating(boolean averageRating, boolean ratingNumber) {
        return averageRating || !ratingNumber;
    }

    /**
     * 선택된 정렬 기준에 맞는 category 상품 목록 조회 쿼리를 호출한다.
     */
    private List<ProductSummaryNativeProjection> findCategoryProductSummaries(
            MainCategory category,
            Long cursorId,
            BigDecimal cursorAverageRating,
            Integer cursorRatingNumber,
            boolean averageRatingSort,
            int limit
    ) {
        if (averageRatingSort) {
            return productRepository.findCategoryProductSummariesOrderByAverageRating(
                    category,
                    cursorId,
                    cursorAverageRating,
                    limit
            );
        }
        return productRepository.findCategoryProductSummariesOrderByRatingNumber(
                category,
                cursorId,
                cursorRatingNumber,
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
     * query parameter로 받은 category 문자열을 도메인 enum으로 변환한다.
     */
    private MainCategory parseCategory(String mainCategory) {
        MainCategory category = MainCategory.fromQueryParam(mainCategory);
        if (category == MainCategory.UNKNOWN) {
            throw new UnknownProductCategoryException();
        }
        return category;
    }

    /**
     * cursor 조합과 cursor 상품의 category 일치 여부를 검증한다.
     */
    private void validateCursor(
            MainCategory mainCategory,
            Long cursorId,
            BigDecimal cursorAverageRating,
            Integer cursorRatingNumber,
            boolean averageRatingSort
    ) {
        boolean sortCursorValueExists = hasSortCursorValue(
                cursorAverageRating,
                cursorRatingNumber,
                averageRatingSort
        );
        validateCursorPair(cursorId, sortCursorValueExists);
        if (cursorId == null) {
            return;
        }
        validateCursorProduct(mainCategory, cursorId);
    }

    /**
     * 현재 정렬 기준에서 필요한 cursor 값이 요청에 포함되어 있는지 확인한다.
     */
    private boolean hasSortCursorValue(
            BigDecimal cursorAverageRating,
            Integer cursorRatingNumber,
            boolean averageRatingSort
    ) {
        return averageRatingSort
                ? cursorAverageRating != null
                : cursorRatingNumber != null;
    }

    /**
     * cursorId와 정렬 cursor 값이 함께 있거나 함께 없는지 검증한다.
     */
    private void validateCursorPair(Long cursorId, boolean sortCursorValueExists) {
        if (cursorId == null && sortCursorValueExists) {
            throw new MissingCategoryCursorIdException();
        }
        if (cursorId != null && !sortCursorValueExists) {
            throw new MissingCategorySortCursorException();
        }
    }

    /**
     * cursorId가 실제 상품이고 요청 category에 속한 상품인지 검증한다.
     */
    private void validateCursorProduct(MainCategory mainCategory, Long cursorId) {
        if (!productRepository.existsById(cursorId)) {
            throw new ProductCategoryCursorNotFoundException();
        }
        if (!productRepository.existsByIdAndMainCategory(cursorId, mainCategory)) {
            throw new ProductCategoryCursorMismatchException();
        }
    }
}
