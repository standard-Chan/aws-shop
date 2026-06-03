package jeong.awsshop.analytics.presentation;

import jakarta.validation.Valid;
import jeong.awsshop.analytics.application.AnalyticsBatchEventService;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsBatchEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics/events/batch")
public class AnalyticsBatchEventController {

    private final AnalyticsBatchEventService analyticsBatchEventService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalyticsBatchEventResponse recordBatch(@Valid @RequestBody AnalyticsBatchEventRequest request) {
        return analyticsBatchEventService.recordBatch(request);
    }
}
