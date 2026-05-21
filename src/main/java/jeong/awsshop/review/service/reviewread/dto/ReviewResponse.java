package jeong.awsshop.review.service.reviewread.dto;

import java.util.List;
import jeong.awsshop.review.repository.projection.ReviewImageProjection;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;

public record ReviewResponse(
        String id,
        Float rating,
        String title,
        String text,
        Long timestamp,
        String userId,
        Boolean verifiedPurchase,
        Integer helpfulVote,
        String asin,
        String parentAsin,
        List<ReviewImageResponse> images
) {

    /**
     * review summary와 image projection 묶음을 응답 DTO로 변환한다.
     */
    public static ReviewResponse from(
            ReviewSummaryProjection row,
            List<ReviewImageProjection> images
    ) {
        return new ReviewResponse(
                String.valueOf(row.getId()),
                row.getRating(),
                row.getTitle(),
                row.getText(),
                row.getTimestamp(),
                row.getUserId(),
                row.getVerifiedPurchase(),
                row.getHelpfulVote(),
                row.getAsin(),
                row.getParentAsin(),
                images.stream()
                        .map(ReviewImageResponse::from)
                        .toList()
        );
    }
}
