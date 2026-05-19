package jeong.awsshop.review.service.reviewread;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jeong.awsshop.review.exception.reviewread.InvalidReviewCursorException;
import jeong.awsshop.review.repository.ReviewImageRepository;
import jeong.awsshop.review.repository.ReviewRepository;
import jeong.awsshop.review.repository.projection.ReviewImageProjection;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;
import jeong.awsshop.review.service.reviewread.dto.ReviewCursorResponse;
import jeong.awsshop.review.service.reviewread.dto.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewReadService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;

    /**
     * 상품 기준 리뷰 cursor 조회 결과를 응답 DTO로 조립한다.
     */
    @Transactional(readOnly = true)
    public ReviewCursorResponse getReviewsByProductId(
            String parentAsin,
            Integer size,
            String sort,
            String direction,
            Long cursorId,
            Long cursorTimestamp,
            Integer cursorHelpfulVote,
            Float cursorRating
    ) {
        String normalizedSort = normalizeSort(sort);
        String normalizedDirection = normalizeDirection(direction);

        validateCursorCombination(
                normalizedSort,
                cursorId,
                cursorTimestamp,
                cursorHelpfulVote,
                cursorRating
        );

        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                parentAsin,
                normalizedSort,
                normalizedDirection,
                cursorId,
                cursorTimestamp,
                cursorHelpfulVote,
                cursorRating,
                size + 1
        );

        List<ReviewSummaryProjection> filteredRows = rows.stream()
                .filter(row -> shouldIncludeRow(row, normalizedSort))
                .toList();
        List<ReviewSummaryProjection> responseRows = filteredRows.stream()
                .limit(size)
                .toList();
        List<ReviewImageProjection> imageRows = responseRows.isEmpty()
                ? List.of()
                : reviewImageRepository.findReviewImagesByReviewIds(
                        responseRows.stream()
                                .map(ReviewSummaryProjection::getId)
                                .toList()
                );
        if (imageRows == null) {
            imageRows = List.of();
        }
        Map<Long, List<ReviewImageProjection>> imagesByReviewId = imageRows.stream()
                .collect(Collectors.groupingBy(ReviewImageProjection::getReviewId));

        List<ReviewResponse> reviews = responseRows.stream()
                .map(row -> ReviewResponse.from(row, imagesByReviewId.getOrDefault(row.getId(), List.of())))
                .toList();

        return ReviewCursorResponse.from(reviews, filteredRows, size, normalizedSort);
    }

    private String normalizeSort(String sort) {
        return "rating".equalsIgnoreCase(sort) ? "rating" : "helpfulVote";
    }

    private String normalizeDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
    }

    /**
     * service 단계에서도 cursor 조합 계약을 다시 검증한다.
     */
    private void validateCursorCombination(
            String sort,
            Long cursorId,
            Long cursorTimestamp,
            Integer cursorHelpfulVote,
            Float cursorRating
    ) {
        boolean hasAnyCursor = cursorId != null
                || cursorTimestamp != null
                || cursorHelpfulVote != null
                || cursorRating != null;

        if (!hasAnyCursor) {
            return;
        }

        if (cursorId == null || cursorTimestamp == null) {
            throw new InvalidReviewCursorException();
        }

        if ("rating".equals(sort)) {
            if (cursorRating == null) {
                throw new InvalidReviewCursorException();
            }
            return;
        }

        if (cursorHelpfulVote == null) {
            throw new InvalidReviewCursorException();
        }
    }

    /**
     * 정렬 기준 값이 null인 row는 응답에서 제외한다.
     */
    private boolean shouldIncludeRow(ReviewSummaryProjection row, String sort) {
        if ("rating".equals(sort)) {
            return row.getRating() != null;
        }
        return row.getHelpfulVote() != null;
    }
}
