package jeong.awsshop.review.exception.reviewread;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReviewReadException extends ResponseStatusException {

    private static final String MESSAGE_PREFIX = "[Review 조회 실패]: ";

    /**
     * review 조회 실패를 400 Bad Request로 응답한다.
     */
    protected ReviewReadException(String reason) {
        super(HttpStatus.BAD_REQUEST, MESSAGE_PREFIX + reason);
    }
}
