package jeong.awsshop.eventpipeline.productranking.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisProductRankingStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-07T06:00:00Z");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisProductRankingStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        store = new RedisProductRankingStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("Redis sorted set bucket에 상품 점수를 누적하고 상위 랭킹을 반환해야 한다")
    void should_accumulate_scores_in_redis_sorted_set_buckets() {
        store.increaseScore(100L, 1L, NOW);
        store.increaseScore(100L, 3L, NOW);
        store.increaseScore(200L, 10L, NOW);

        var rankings = store.findTop(RankingWindow.ONE_HOUR, 10, NOW);

        assertThat(rankings)
                .extracting("rank", "productId", "score")
                .containsExactly(
                        tuple(1L, 200L, 10L),
                        tuple(2L, 100L, 4L)
                );
    }

    @Test
    @DisplayName("여러 상품 점수를 Redis pipeline batch로 누적해야 한다")
    void should_accumulate_scores_by_batch() {
        store.increaseScores(List.of(
                new ProductRankingScoreDelta(100L, 1L, NOW),
                new ProductRankingScoreDelta(100L, 3L, NOW),
                new ProductRankingScoreDelta(200L, 10L, NOW)
        ));

        var rankings = store.findTop(RankingWindow.ONE_HOUR, 10, NOW);

        assertThat(rankings)
                .extracting("rank", "productId", "score")
                .containsExactly(
                        tuple(1L, 200L, 10L),
                        tuple(2L, 100L, 4L)
                );
    }

    @Test
    @DisplayName("시간 윈도우 밖의 bucket은 Redis 합산 랭킹에서 제외해야 한다")
    void should_filter_buckets_by_ranking_window() {
        store.increaseScore(100L, 10L, NOW.minusSeconds(30 * 60));
        store.increaseScore(200L, 10L, NOW.minusSeconds(2 * 60 * 60));
        store.increaseScore(300L, 10L, NOW.minusSeconds(2 * 24 * 60 * 60));
        store.increaseScore(400L, 10L, NOW.minusSeconds(8 * 24 * 60 * 60));

        assertThat(store.findTop(RankingWindow.ONE_HOUR, 10, NOW))
                .extracting("productId", "score")
                .containsExactly(tuple(100L, 10L));
        assertThat(store.findTop(RankingWindow.ONE_DAY, 10, NOW))
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 10L)
                );
        assertThat(store.findTop(RankingWindow.ONE_WEEK, 10, NOW))
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 10L),
                        tuple(300L, 10L)
                );
    }

    @Test
    @DisplayName("동점이면 기존 구현처럼 productId 오름차순으로 순위를 결정해야 한다")
    void should_sort_same_score_by_product_id_ascending() {
        store.increaseScore(300L, 10L, NOW);
        store.increaseScore(100L, 10L, NOW);
        store.increaseScore(200L, 10L, NOW);

        var rankings = store.findTop(RankingWindow.ONE_HOUR, 10, NOW);

        assertThat(rankings)
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 10L),
                        tuple(300L, 10L)
                );
    }

    @Test
    @DisplayName("bucket key에는 retention보다 긴 TTL을 설정해야 한다")
    void should_set_ttl_on_bucket_key() {
        store.increaseScore(100L, 10L, NOW);

        Long ttl = redisTemplate.getExpire("product-ranking:bucket:" + NOW.toEpochMilli() / 60_000L);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(7 * 24 * 60 * 60L);
    }

    @Test
    @DisplayName("limit 개수만큼만 랭킹을 반환해야 한다")
    void should_return_rankings_by_limit() {
        store.increaseScore(100L, 10L, NOW);
        store.increaseScore(200L, 9L, NOW);
        store.increaseScore(300L, 8L, NOW);

        var rankings = store.findTop(RankingWindow.ONE_HOUR, 2, NOW);

        assertThat(rankings)
                .extracting("productId", "score")
                .containsExactly(
                        tuple(100L, 10L),
                        tuple(200L, 9L)
                );
    }

    @Test
    @DisplayName("Redis 서버가 보고하는 전체 사용 메모리를 반환해야 한다")
    void should_return_redis_used_memory_bytes() {
        store.increaseScore(100L, 10L, NOW);

        long redisUsedMemoryBytes = store.redisUsedMemoryBytes();

        assertThat(redisUsedMemoryBytes).isPositive();
    }
}
