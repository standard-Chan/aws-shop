package jeong.awsshop.analytics.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * analytics_events 테이블 저장/조회에 사용하는 JPA repository다.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsStoredEvent, Long> {

    /**
     * Funnel/Summary KPI가 같은 기준으로 쓸 수 있게 기간 내 이벤트 타입별 수를 집계한다.
     */
    @Query("""
            select e.eventType as eventType, count(e) as count
            from AnalyticsStoredEvent e
            where e.occurredAt >= :from
              and e.occurredAt < :to
            group by e.eventType
            """)
    List<AnalyticsEventTypeCount> countByEventTypeBetween(
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    /**
     * 상품별 KPI는 조회 이벤트가 있는 상품만 대상으로 하고, 장바구니 수를 함께 붙인다.
     */
    @Query("""
            select e.productId as productId,
                   sum(case when e.eventType = :productViewType then 1 else 0 end) as productViewCount,
                   sum(case when e.eventType = :addToCartType then 1 else 0 end) as addToCartCount
            from AnalyticsStoredEvent e
            where e.occurredAt >= :from
              and e.occurredAt < :to
              and e.productId is not null
              and e.eventType in (:productViewType, :addToCartType)
            group by e.productId
            having sum(case when e.eventType = :productViewType then 1 else 0 end) > 0
            order by sum(case when e.eventType = :productViewType then 1 else 0 end) desc,
                     e.productId asc
            """)
    List<AnalyticsProductKpiCount> findProductKpiCounts(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("productViewType") AnalyticsEventType productViewType,
            @Param("addToCartType") AnalyticsEventType addToCartType,
            Pageable pageable
    );

    /**
     * 검색어별 CTR 계산을 위해 SEARCH.keyword와 PRODUCT_VIEW.keyword를 같은 축으로 집계한다.
     */
    @Query("""
            select e.keyword as keyword,
                   sum(case when e.eventType = :searchType then 1 else 0 end) as searchCount,
                   sum(case when e.eventType = :productViewType then 1 else 0 end) as productViewCount
            from AnalyticsStoredEvent e
            where e.occurredAt >= :from
              and e.occurredAt < :to
              and e.keyword is not null
              and e.eventType in (:searchType, :productViewType)
            group by e.keyword
            having sum(case when e.eventType = :searchType then 1 else 0 end) > 0
            order by sum(case when e.eventType = :searchType then 1 else 0 end) desc,
                     e.keyword asc
            """)
    List<AnalyticsKeywordKpiCount> findKeywordKpiCounts(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("searchType") AnalyticsEventType searchType,
            @Param("productViewType") AnalyticsEventType productViewType,
            Pageable pageable
    );
}
