package jeong.awsshop.analytics.presentation;

import jeong.awsshop.analytics.exception.AnalyticsEventPublishException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AnalyticsEventController.class)
public class AnalyticsEventExceptionHandler {

    @ExceptionHandler(AnalyticsEventPublishException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handlePublishException(AnalyticsEventPublishException exception) {
        return exception.getMessage();
    }
}
