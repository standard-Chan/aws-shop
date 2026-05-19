package jeong.awsshop.review.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
}
