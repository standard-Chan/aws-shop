package jeong.awsshop.product.service.productread;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.exception.productread.MissingCategoryCursorIdException;
import jeong.awsshop.product.exception.productread.MissingCategorySortCursorException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorMismatchException;
import jeong.awsshop.product.exception.productread.ProductCategoryCursorNotFoundException;
import jeong.awsshop.product.exception.productread.UnknownProductCategoryException;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductRepository productRepository;

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
