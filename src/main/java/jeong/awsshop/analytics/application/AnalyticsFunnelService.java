package jeong.awsshop.analytics.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.domain.AnalyticsEventTypeCount;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelStepResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AnalyticsFunnelService {

    private static final String BASIS = "EVENT_COUNT";
    private static final List<AnalyticsEventType> FUNNEL_STEPS = List.of(
            AnalyticsEventType.SEARCH,
            AnalyticsEventType.PRODUCT_VIEW,
            AnalyticsEventType.ADD_TO_CART,
            AnalyticsEventType.PURCHASE
    );

    private final AnalyticsEventRepository analyticsEventRepository;

    public AnalyticsFunnelResponse getFunnel(Instant from, Instant to) {
        validatePeriod(from, to);

        // Repository는 실제로 존재하는 eventType만 반환하므로, 이후 고정된 퍼널 순서로 재구성하기 위해 Map으로 옮긴다.
        Map<AnalyticsEventType, Long> counts = new EnumMap<>(AnalyticsEventType.class);
        for (AnalyticsEventTypeCount count : analyticsEventRepository.countByEventTypeBetween(from, to)) {
            counts.put(count.getEventType(), count.getCount());
        }

        long searchCount = counts.getOrDefault(AnalyticsEventType.SEARCH, 0L);
        Long previousCount = null;
        List<AnalyticsFunnelStepResponse> steps = new ArrayList<>();

        // 응답은 조회 결과 순서가 아니라 비즈니스 퍼널 순서로 고정한다.
        // 없는 단계는 0으로 채워서 클라이언트가 항상 같은 4단계 구조를 받을 수 있게 한다.
        for (AnalyticsEventType eventType : FUNNEL_STEPS) {
            long currentCount = counts.getOrDefault(eventType, 0L);
            steps.add(new AnalyticsFunnelStepResponse(
                    eventType,
                    currentCount,
                    conversionRate(currentCount, previousCount),
                    conversionRateFromSearch(eventType, currentCount, searchCount)
            ));
            previousCount = currentCount;
        }

        return new AnalyticsFunnelResponse(from, to, BASIS, steps);
    }

    private void validatePeriod(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
    }

    // 첫 단계 SEARCH는 직전 단계가 없으므로 null을 반환한다.
    // 그 외 단계에서 직전 단계 count가 0이면 나눗셈 대신 0.0으로 고정한다.
    private Double conversionRate(long numerator, Long denominator) {
        if (denominator == null) {
            return null;
        }
        if (denominator == 0L) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    // SEARCH 대비 전환율은 모든 단계에서 숫자로 내려준다.
    // SEARCH 단계 자체는 데이터가 있으면 1.0, 검색 이벤트가 없으면 0.0이다.
    private double conversionRateFromSearch(AnalyticsEventType eventType, long count, long searchCount) {
        if (eventType == AnalyticsEventType.SEARCH) {
            return searchCount == 0L ? 0.0 : 1.0;
        }
        if (searchCount == 0L) {
            return 0.0;
        }
        return (double) count / searchCount;
    }
}
