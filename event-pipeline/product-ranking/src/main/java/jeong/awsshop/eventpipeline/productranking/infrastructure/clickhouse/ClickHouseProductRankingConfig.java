package jeong.awsshop.eventpipeline.productranking.infrastructure.clickhouse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ClickHouse 기능을 명시적으로 켰을 때만 properties binding과 저장소 Bean을 활성화한다.
 *
 * <p>Redis만으로 product-ranking을 실행하는 로컬/테스트 환경에서는 ClickHouse 연결 시도를 하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(ClickHouseProductRankingProperties.class)
@ConditionalOnProperty(
        prefix = "event-pipeline.product-ranking.clickhouse",
        name = "enabled",
        havingValue = "true"
)
public class ClickHouseProductRankingConfig {
}
