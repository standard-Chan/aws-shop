package jeong.awsshop.analytics.presentation;

import java.time.Instant;
import jeong.awsshop.analytics.application.AnalyticsFunnelService;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics/funnel")
public class AnalyticsFunnelController {

    private final AnalyticsFunnelService analyticsFunnelService;

    @GetMapping
    public AnalyticsFunnelResponse getFunnel(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return analyticsFunnelService.getFunnel(from, to);
    }
}
