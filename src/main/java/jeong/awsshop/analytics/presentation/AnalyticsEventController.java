package jeong.awsshop.analytics.presentation;

import jakarta.validation.Valid;
import jeong.awsshop.analytics.application.AnalyticsEventService;
import jeong.awsshop.analytics.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.analytics.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.analytics.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics/events")
public class AnalyticsEventController {

    private final AnalyticsEventService analyticsEventService;

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalyticsEventResponse recordSearch(@Valid @RequestBody SearchEventRequest request) {
        return analyticsEventService.recordSearch(request);
    }

    @PostMapping("/product-view")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalyticsEventResponse recordProductView(@Valid @RequestBody ProductViewEventRequest request) {
        return analyticsEventService.recordProductView(request);
    }

    @PostMapping("/add-to-cart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalyticsEventResponse recordAddToCart(@Valid @RequestBody AddToCartEventRequest request) {
        return analyticsEventService.recordAddToCart(request);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalyticsEventResponse recordPurchase(@Valid @RequestBody PurchaseEventRequest request) {
        return analyticsEventService.recordPurchase(request);
    }
}
