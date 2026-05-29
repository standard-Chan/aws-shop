package jeong.awsshop.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * analytics_events 테이블 저장/조회에 사용하는 JPA repository다.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsStoredEvent, Long> {
}
