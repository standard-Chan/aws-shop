package jeong.awsshop.review.exception.reviewread;

public class InvalidReviewCursorException extends ReviewReadException {

    private static final String MESSAGE = "cursor 조합이 올바르지 않습니다.";

    /**
     * 리뷰 cursor 조합이 불완전할 때 사용하는 예외다.
     */
    public InvalidReviewCursorException() {
        super(MESSAGE);
    }
}
