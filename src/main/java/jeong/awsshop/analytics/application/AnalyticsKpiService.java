package jeong.awsshop.analytics.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.domain.AnalyticsEventTypeCount;
import jeong.awsshop.analytics.domain.AnalyticsKeywordKpiCount;
import jeong.awsshop.analytics.domain.AnalyticsProductKpiCount;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiItemResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKpiSummaryResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiItemResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AnalyticsKpiService {

    private static final String BASIS = "EVENT_COUNT";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final AnalyticsEventRepository analyticsEventRepository;

    /**
     * 기간 내 전체 이벤트 흐름을 한 화면용 KPI로 요약한다.
     */
    public AnalyticsKpiSummaryResponse getSummary(Instant from, Instant to) {
        validatePeriod(from, to);

        // Repository 결과는 존재 하는 타입만 오므로, 누락 타입을 0으로 채우기 위해 Map으로 정규화한다.
        Map<AnalyticsEventType, Long> counts = new EnumMap<>(AnalyticsEventType.class);
        for (AnalyticsEventTypeCount count : analyticsEventRepository.countByEventTypeBetween(from, to)) {
            counts.put(count.getEventType(), count.getCount());
        }

        long searchCount = counts.getOrDefault(AnalyticsEventType.SEARCH, 0L);
        long productViewCount = counts.getOrDefault(AnalyticsEventType.PRODUCT_VIEW, 0L);
        long addToCartCount = counts.getOrDefault(AnalyticsEventType.ADD_TO_CART, 0L);
        long purchaseCount = counts.getOrDefault(AnalyticsEventType.PURCHASE, 0L);

        return new AnalyticsKpiSummaryResponse(
                from,
                to,
                BASIS,
                searchCount,
                productViewCount,
                addToCartCount,
                purchaseCount,
                rate(productViewCount, searchCount),
                rate(addToCartCount, productViewCount),
                rate(purchaseCount, productViewCount)
        );
    }

    /**
     * 상품별 조회-장바구니 전환을 비교할 수 있게 조회 수 기준 상위 상품을 반환한다.
     */
    public AnalyticsProductKpiResponse getProducts(Instant from, Instant to, Integer limit) {
        validatePeriod(from, to);
        int normalizedLimit = validateAndNormalizeLimit(limit);

        List<AnalyticsProductKpiItemResponse> items = analyticsEventRepository.findProductKpiCounts(
                        from,
                        to,
                        AnalyticsEventType.PRODUCT_VIEW,
                        AnalyticsEventType.ADD_TO_CART,
                        PageRequest.of(0, normalizedLimit)
                )
                .stream()
                .map(this::toProductItem)
                .toList();

        return new AnalyticsProductKpiResponse(from, to, BASIS, items);
    }

    /**
     * 검색어별 검색-상품조회 전환을 CTR 형태로 반환한다.
     */
    public AnalyticsKeywordKpiResponse getKeywords(Instant from, Instant to, Integer limit) {
        validatePeriod(from, to);
        int normalizedLimit = validateAndNormalizeLimit(limit);

        // TODO : 추후에는 검색어수가 많아져, 일괄 조회가 어려울 수 있다.
        //  이 경우에는 검색어 기준으로 페이지네이션을 하거나, 검색어별로 개별 조회하는 방법도 고려해볼 수 있다.
        List<AnalyticsKeywordKpiItemResponse> items = analyticsEventRepository.findKeywordKpiCounts(
                        from,
                        to,
                        AnalyticsEventType.SEARCH,
                        AnalyticsEventType.PRODUCT_VIEW,
                        PageRequest.of(0, normalizedLimit)
                )
                .stream()
                .map(this::toKeywordItem)
                .toList();

        return new AnalyticsKeywordKpiResponse(from, to, BASIS, items);
    }

    /**
     * V1에서는 상품별 구매 귀속이 불가능하므로 purchaseRate를 null로 명시한다.
     */
    private AnalyticsProductKpiItemResponse toProductItem(AnalyticsProductKpiCount count) {
        return new AnalyticsProductKpiItemResponse(
                count.getProductId(),
                count.getProductViewCount(),
                count.getAddToCartCount(),
                rate(count.getAddToCartCount(), count.getProductViewCount()),
                null
        );
    }

    /**
     * 검색어별 조회 수를 검색 수로 나눠 keyword CTR을 만든다.
     */
    private AnalyticsKeywordKpiItemResponse toKeywordItem(AnalyticsKeywordKpiCount count) {
        return new AnalyticsKeywordKpiItemResponse(
                count.getKeyword(),
                count.getSearchCount(),
                count.getProductViewCount(),
                rate(count.getProductViewCount(), count.getSearchCount())
        );
    }

    /**
     * 모든 분석 API는 같은 반열림 기간 조건을 사용한다.
     */
    private void validatePeriod(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
    }

    /**
     * 목록 API가 과도한 집계를 한 번에 반환하지 않도록 limit을 고정 범위로 제한한다.
     */
    private int validateAndNormalizeLimit(Integer limit) {
        int normalizedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (normalizedLimit < MIN_LIMIT || normalizedLimit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        }
        return normalizedLimit;
    }

    /**
     * 분석 응답에서는 분모가 0인 비율을 예외 대신 0.0으로 표현한다.
     */
    private double rate(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }
}
