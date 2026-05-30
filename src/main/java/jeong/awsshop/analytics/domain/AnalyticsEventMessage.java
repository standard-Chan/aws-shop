package jeong.awsshop.analytics.domain;

import java.time.Instant;

public record AnalyticsEventMessage(
        Long eventId,
        AnalyticsEventType eventType,
        Long userId,
        Instant occurredAt,
        String keyword,
        Long productId,
        Long orderId,
        Long searchEventId
) {

    /**
     * 검색 이벤트는 keyword를 KPI 집계 차원으로 직접 보관한다.
     */
    public static AnalyticsEventMessage search(Long eventId, Long userId, Instant occurredAt, String keyword) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.SEARCH,
                userId,
                occurredAt,
                keyword,
                null,
                null,
                null
        );
    }

    /**
     * 기존 product-view 요청 호환을 위해 검색 컨텍스트 없이도 메시지를 만들 수 있게 둔다.
     */
    public static AnalyticsEventMessage productView(Long eventId, Long userId, Instant occurredAt, Long productId) {
        return productView(eventId, userId, occurredAt, productId, null, null);
    }

    /**
     * 검색에서 이어진 상품 조회라면 searchEventId와 keyword를 함께 남겨 검색어별 CTR을 계산한다.
     */
    public static AnalyticsEventMessage productView(
            Long eventId,
            Long userId,
            Instant occurredAt,
            Long productId,
            Long searchEventId,
            String searchKeyword
    ) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.PRODUCT_VIEW,
                userId,
                occurredAt,
                searchKeyword,
                productId,
                null,
                searchEventId
        );
    }

    /**
     * 상품별 장바구니 전환율 계산을 위해 productId를 보관한다.
     */
    public static AnalyticsEventMessage addToCart(Long eventId, Long userId, Instant occurredAt, Long productId) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.ADD_TO_CART,
                userId,
                occurredAt,
                null,
                productId,
                null,
                null
        );
    }

    /**
     * V1 구매 이벤트는 주문 단위 집계만 가능하므로 orderId만 보관한다.
     */
    public static AnalyticsEventMessage purchase(Long eventId, Long userId, Instant occurredAt, Long orderId) {
        return new AnalyticsEventMessage(
                eventId,
                AnalyticsEventType.PURCHASE,
                userId,
                occurredAt,
                null,
                null,
                orderId,
                null
        );
    }
}
