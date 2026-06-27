package jeong.awsshop.eventpipeline.productranking.infrastructure.clickhouse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ClickHouse 랭킹 저장소 설정이다.
 *
 * <p>{@code enabled=false}가 기본값이라 Redis 단독 실행 경로를 깨지 않는다.
 * ClickHouse 비교/장기 window 조회를 켤 때만 관련 Bean이 생성된다.
 */
@ConfigurationProperties(prefix = "event-pipeline.product-ranking.clickhouse")
public record ClickHouseProductRankingProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        boolean initializeSchema
) {
}
