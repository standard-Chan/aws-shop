package jeong.awsshop.review.service.reviewread.dto;

public record ReviewCursor(
        Long id,
        Long timestamp,
        Integer helpfulVote,
        Float rating
) {

    /**
     * helpfulVote 정렬 응답에 사용할 cursor를 만든다.
     */
    public static ReviewCursor helpfulVoteCursor(ReviewResponse review) {
        return new ReviewCursor(review.id(), review.timestamp(), review.helpfulVote(), null);
    }

    /**
     * rating 정렬 응답에 사용할 cursor를 만든다.
     */
    public static ReviewCursor ratingCursor(ReviewResponse review) {
        return new ReviewCursor(review.id(), review.timestamp(), null, review.rating());
    }
}
