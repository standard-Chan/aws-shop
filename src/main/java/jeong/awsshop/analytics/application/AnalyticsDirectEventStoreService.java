package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.presentation.dto.AddToCartEventRequest;
import jeong.awsshop.analytics.presentation.dto.AnalyticsEventResponse;
import jeong.awsshop.analytics.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.analytics.presentation.dto.PurchaseEventRequest;
import jeong.awsshop.analytics.presentation.dto.SearchEventRequest;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 부하테스트에서 Kafka 도입 전 기준선을 재현하기 위한 동기 DB 저장 서비스다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.analytics.benchmark.direct-store-enabled", havingValue = "true")
public class AnalyticsDirectEventStoreService {

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final AnalyticsEventStoreService analyticsEventStoreService;
    private final Clock clock;

    public AnalyticsEventResponse recordSearch(SearchEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.search(
                nextEventId(),
                request.userId(),
                now(),
                request.keyword()
        );
        analyticsEventStoreService.saveIfAbsent(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordProductView(ProductViewEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.productView(
                nextEventId(),
                request.userId(),
                now(),
                request.productId(),
                request.searchEventId(),
                request.searchKeyword()
        );
        analyticsEventStoreService.saveIfAbsent(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordAddToCart(AddToCartEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.addToCart(
                nextEventId(),
                request.userId(),
                now(),
                request.productId()
        );
        analyticsEventStoreService.saveIfAbsent(event);
        return AnalyticsEventResponse.from(event);
    }

    public AnalyticsEventResponse recordPurchase(PurchaseEventRequest request) {
        AnalyticsEventMessage event = AnalyticsEventMessage.purchase(
                nextEventId(),
                request.userId(),
                now(),
                request.orderId()
        );
        analyticsEventStoreService.saveIfAbsent(event);
        return AnalyticsEventResponse.from(event);
    }

    private Long nextEventId() {
        return snowflakeIdGenerator.nextId();
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
