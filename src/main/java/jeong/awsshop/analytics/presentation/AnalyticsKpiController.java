package jeong.awsshop.analytics.presentation;

import java.time.Instant;
import jeong.awsshop.analytics.application.AnalyticsKpiService;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKpiSummaryResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics/kpis")
public class AnalyticsKpiController {

    private final AnalyticsKpiService analyticsKpiService;

    /**
     * 전체 퍼널 흐름을 운영 지표로 빠르게 확인하는 요약 API다.
     */
    @GetMapping("/summary")
    public AnalyticsKpiSummaryResponse getSummary(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return analyticsKpiService.getSummary(from, to);
    }

    /**
     * 상품별 조회 대비 장바구니 전환을 상위 N개로 조회한다.
     */
    @GetMapping("/products")
    public AnalyticsProductKpiResponse getProducts(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        return analyticsKpiService.getProducts(from, to, limit);
    }

    /**
     * 검색어별 검색 대비 상품 조회 전환을 상위 N개로 조회한다.
     */
    @GetMapping("/keywords")
    public AnalyticsKeywordKpiResponse getKeywords(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Integer limit
    ) {
        return analyticsKpiService.getKeywords(from, to, limit);
    }
}
