package jeong.awsshop.product.service.productread;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        boolean averageRatingSort = averageRating || !ratingNumber;
        validateCursor(category, cursorId, cursorAverageRating, cursorRatingNumber, averageRatingSort);

        List<ProductSummaryNativeProjection> rows = averageRatingSort
                ? productRepository.findCategoryProductSummariesOrderByAverageRating(
                category,
                cursorId,
                cursorAverageRating,
                queryLimitForHasNext(size)
        )
                : productRepository.findCategoryProductSummariesOrderByRatingNumber(
                category,
                cursorId,
                cursorRatingNumber,
                queryLimitForHasNext(size)
        );

        return ProductCategoryCursorResponse.from(rows, size, averageRatingSort);
    }

    private int queryLimitForHasNext(int size) {
        return size + 1;
    }

    private MainCategory parseCategory(String mainCategory) {
        MainCategory category = MainCategory.fromQueryParam(mainCategory);
        if (category == MainCategory.UNKNOWN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: 알 수 없는 category입니다.");
        }
        return category;
    }

    private void validateCursor(
            MainCategory mainCategory,
            Long cursorId,
            BigDecimal cursorAverageRating,
            Integer cursorRatingNumber,
            boolean averageRatingSort
    ) {
        boolean sortCursorExists = averageRatingSort
                ? cursorAverageRating != null
                : cursorRatingNumber != null;
        if (cursorId == null && sortCursorExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: cursorId가 필요합니다.");
        }
        if (cursorId != null && !sortCursorExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: 정렬 cursor 값이 필요합니다.");
        }
        if (cursorId == null) {
            return;
        }
        if (!productRepository.existsById(cursorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: 존재하지 않는 cursorId입니다.");
        }
        if (!productRepository.existsByIdAndMainCategory(cursorId, mainCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: cursor category가 일치하지 않습니다.");
        }
    }
}
