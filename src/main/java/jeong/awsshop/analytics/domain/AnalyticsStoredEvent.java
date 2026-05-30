package jeong.awsshop.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka에서 소비한 사용자 행동 이벤트를 analytics_events 테이블에 저장하는 엔티티다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "analytics_events",
        indexes = {
                @Index(name = "idx_analytics_events_type_occurred_at", columnList = "event_type, occurred_at"),
                @Index(name = "idx_analytics_events_user_occurred_at", columnList = "user_id, occurred_at"),
                @Index(name = "idx_analytics_events_search_event_id", columnList = "search_event_id"),
                @Index(name = "idx_analytics_events_type_product_occurred_at", columnList = "event_type, product_id, occurred_at"),
                @Index(name = "idx_analytics_events_type_keyword_occurred_at", columnList = "event_type, keyword, occurred_at")
        }
)
public class AnalyticsStoredEvent {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AnalyticsEventType eventType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "keyword")
    private String keyword;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "search_event_id")
    private Long searchEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private AnalyticsStoredEvent(
            Long eventId,
            AnalyticsEventType eventType,
            Long userId,
            Instant occurredAt,
            String keyword,
            Long productId,
            Long orderId,
            Long searchEventId,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.userId = userId;
        this.occurredAt = occurredAt;
        this.keyword = keyword;
        this.productId = productId;
        this.orderId = orderId;
        this.searchEventId = searchEventId;
        this.createdAt = createdAt;
    }

    /**
     * Consumer 저장 단계에서 Kafka 메시지를 DB 저장 엔티티로 변환한다.
     * PRODUCT_VIEW의 searchKeyword는 keyword 컬럼에 함께 저장해 검색어별 집계를 단순화한다.
     */
    public static AnalyticsStoredEvent from(AnalyticsEventMessage message, Instant createdAt) {
        return new AnalyticsStoredEvent(
                message.eventId(),
                message.eventType(),
                message.userId(),
                message.occurredAt(),
                message.keyword(),
                message.productId(),
                message.orderId(),
                message.searchEventId(),
                createdAt
        );
    }
}
