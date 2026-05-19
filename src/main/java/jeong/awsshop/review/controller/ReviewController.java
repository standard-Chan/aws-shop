package jeong.awsshop.review.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jeong.awsshop.review.exception.reviewread.InvalidReviewCursorException;
import jeong.awsshop.review.service.reviewread.ReviewReadService;
import jeong.awsshop.review.service.reviewread.dto.ReviewCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewReadService reviewReadService;

    /**
     * 상품 기준으로 리뷰를 cursor 방식으로 조회한다.
     */
    @GetMapping("/products/{parentAsin}")
    public ReviewCursorResponse getReviewsByProductId(
            @PathVariable String parentAsin,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "helpfulVote") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Long cursorTimestamp,
            @RequestParam(required = false) Integer cursorHelpfulVote,
            @RequestParam(required = false) Float cursorRating
    ) {
        validateCursorCombination(sort, cursorId, cursorTimestamp, cursorHelpfulVote, cursorRating);
        return reviewReadService.getReviewsByProductId(
                parentAsin,
                size,
                sort,
                direction,
                cursorId,
                cursorTimestamp,
                cursorHelpfulVote,
                cursorRating
        );
    }

    /**
     * controller 단계에서 불완전한 cursor 조합을 차단한다.
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

        if ("rating".equalsIgnoreCase(sort)) {
            if (cursorRating == null) {
                throw new InvalidReviewCursorException();
            }
            return;
        }

        if (cursorHelpfulVote == null) {
            throw new InvalidReviewCursorException();
        }
    }
}
