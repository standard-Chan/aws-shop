package jeong.awsshop.analytics.presentation;

import jakarta.validation.Valid;
import jeong.awsshop.analytics.application.AnalyticsDirectEventStoreService;
import jeong.awsshop.analytics.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.analytics.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.analytics.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kafka 도입 전 Direct DB 저장 기준선을 측정하기 위한 벤치마크 전용 API다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics/benchmark/events/direct")
@ConditionalOnProperty(name = "app.analytics.benchmark.direct-store-enabled", havingValue = "true")
public class AnalyticsBenchmarkEventController {

    private final AnalyticsDirectEventStoreService analyticsDirectEventStoreService;

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalyticsEventResponse recordSearch(@Valid @RequestBody SearchEventRequest request) {
        return analyticsDirectEventStoreService.recordSearch(request);
    }

    @PostMapping("/product-view")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalyticsEventResponse recordProductView(@Valid @RequestBody ProductViewEventRequest request) {
        return analyticsDirectEventStoreService.recordProductView(request);
    }

    @PostMapping("/add-to-cart")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalyticsEventResponse recordAddToCart(@Valid @RequestBody AddToCartEventRequest request) {
        return analyticsDirectEventStoreService.recordAddToCart(request);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalyticsEventResponse recordPurchase(@Valid @RequestBody PurchaseEventRequest request) {
        return analyticsDirectEventStoreService.recordPurchase(request);
    }
}
