package jeong.awsshop.review.service.reviewread.dto;

import java.util.List;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;

public record ReviewCursorResponse(
        List<ReviewResponse> reviews,
        ReviewCursor nextCursor,
        boolean hasNext
) {

    /**
     * review cursor 조회 결과를 응답으로 조립한다.
     */
    public static ReviewCursorResponse from(
            List<ReviewResponse> reviews,
            List<ReviewSummaryProjection> filteredRows,
            int size,
            String sort
    ) {
        boolean hasNext = filteredRows.size() > size;
        ReviewCursor nextCursor = reviews.isEmpty()
                ? null
                : cursorFrom(reviews.getLast(), sort);

        return new ReviewCursorResponse(reviews, nextCursor, hasNext);
    }

    private static ReviewCursor cursorFrom(ReviewResponse review, String sort) {
        if ("rating".equals(sort)) {
            return ReviewCursor.ratingCursor(review);
        }
        return ReviewCursor.helpfulVoteCursor(review);
    }
}
