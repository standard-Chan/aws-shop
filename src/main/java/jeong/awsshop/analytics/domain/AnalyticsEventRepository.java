package jeong.awsshop.analytics.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * analytics_events 테이블 저장/조회에 사용하는 JPA repository다.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsStoredEvent, Long> {

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
}
