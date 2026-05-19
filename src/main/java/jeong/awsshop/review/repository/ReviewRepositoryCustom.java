package jeong.awsshop.review.repository;

import java.util.List;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;

public interface ReviewRepositoryCustom {

    List<ReviewSummaryProjection> findReviewSummaries(
            String parentAsin,
            String sort,
            String direction,
            Long cursorId,
            Long cursorTimestamp,
            Integer cursorHelpfulVote,
            Float cursorRating,
            Integer limit
    );
}
