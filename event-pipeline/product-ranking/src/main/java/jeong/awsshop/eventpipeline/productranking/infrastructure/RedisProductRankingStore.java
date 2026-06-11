package jeong.awsshop.eventpipeline.productranking.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!in-memory-ranking")
public class RedisProductRankingStore implements ProductRankingStore {

    // 분 단위 bucket으로 쓰기 비용을 낮추고, 조회 시 필요한 시간 범위만 합산한다.
    private static final String KEY_PREFIX = "product-ranking";
    private static final String BUCKET_KEY_PREFIX = KEY_PREFIX + ":bucket:";
    private static final String TEMP_KEY_PREFIX = KEY_PREFIX + ":tmp:";
    private static final long ESTIMATED_BYTES_PER_ENTRY = 128L;
    private static final Duration BUCKET_SIZE = Duration.ofMinutes(1);
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final Duration BUCKET_TTL = RETENTION.plusDays(1);
    private static final Duration TEMP_KEY_TTL = Duration.ofMinutes(1);
    private static final long PRODUCT_ID_PAD_BASE = Long.MAX_VALUE;

    private final StringRedisTemplate redisTemplate;

    public RedisProductRankingStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 이벤트 발생 시각의 분 단위 bucket ZSET에 상품 점수를 누적한다.
     */
    @Override
    public void increaseScore(Long productId, long score, Instant occurredAt) {
        String key = bucketKey(occurredAt);
        // ZSET score를 누적하면 같은 상품의 여러 이벤트 점수가 Redis 안에서 바로 합산된다.
        redisTemplate.opsForZSet().incrementScore(key, member(productId), score);
        // 오래된 bucket은 Redis TTL로 자연스럽게 제거한다.
        redisTemplate.expire(key, BUCKET_TTL);
    }

    /**
     * 여러 이벤트 점수 누적 명령을 Redis pipeline으로 한 번에 전송한다.
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void increaseScores(List<ProductRankingScoreDelta> deltas) {
        if (deltas.isEmpty()) {
            return;
        }

        Set<String> touchedBucketKeys = new LinkedHashSet<>();
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                ZSetOperations<String, String> zSetOperations = operations.opsForZSet();
                for (ProductRankingScoreDelta delta : deltas) {
                    String key = bucketKey(delta.occurredAt());
                    touchedBucketKeys.add(key);
                    zSetOperations.incrementScore(key, member(delta.productId()), delta.score());
                }
                // 같은 batch에서 같은 bucket을 여러 번 만져도 TTL 명령은 bucket당 한 번만 보낸다.
                for (String key : touchedBucketKeys) {
                    operations.expire(key, BUCKET_TTL);
                }
                return null;
            }
        });
    }

    /**
     * 조회 window에 포함되는 bucket들을 합산한 뒤 점수 내림차순 랭킹을 반환한다.
     */
    @Override
    public List<ProductRankingItem> findTop(RankingWindow window, int limit, Instant now) {
        if (limit <= 0) {
            return List.of();
        }

        // 시간 범위 이내의 모든 Key 목록 생성
        List<String> bucketKeys = bucketKeys(window, now);
        // 합산 결과 저장용 임시 키
        String destinationKey = TEMP_KEY_PREFIX + UUID.randomUUID();

        try {
            // Redis가 bucket ZSET 점수를 임시 key에 합산해 window 총점을 계산한다.
            String firstKey = bucketKeys.get(0);
            List<String> otherKeys = bucketKeys.subList(1, bucketKeys.size());
            Long mergedCount = redisTemplate.opsForZSet().unionAndStore(firstKey, otherKeys, destinationKey);
            redisTemplate.expire(destinationKey, TEMP_KEY_TTL);

            if (mergedCount == null || mergedCount == 0) {
                return List.of();
            }

            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(destinationKey, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            // Redis 결과 순서대로 1부터 rank를 붙여 응답 모델로 변환한다.
            List<ZSetOperations.TypedTuple<String>> rankedScores = new ArrayList<>(tuples);
            return IntStream.range(0, rankedScores.size())
                    .mapToObj(index -> new ProductRankingItem(
                            index + 1L,
                            productId(rankedScores.get(index).getValue()),
                            score(rankedScores.get(index).getScore())
                    ))
                    .toList();
        } finally {
            // 임시 합산 key는 요청 처리 후 즉시 삭제해 Redis에 불필요한 key를 남기지 않는다.
            redisTemplate.delete(destinationKey);
        }
    }

    /**
     * 현재 살아 있는 ranking bucket들의 ZSET member 수를 합산한다.
     */
    @Override
    public long hashLength() {
        Set<String> keys = redisTemplate.keys(BUCKET_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }

        return keys.stream()
                .map(redisTemplate.opsForZSet()::zCard)
                .mapToLong(count -> count == null ? 0L : count)
                .sum();
    }

    /**
     * 기존 memory API 호환을 위해 entry 수 기반 추정 메모리 값을 반환한다.
     */
    @Override
    public long estimatedHashMemoryBytes() {
        return hashLength() * ESTIMATED_BYTES_PER_ENTRY;
    }

    /**
     * 기존 memory API 호환을 위해 entry 1개당 추정 바이트 값을 반환한다.
     */
    @Override
    public long estimatedBytesPerEntry() {
        return ESTIMATED_BYTES_PER_ENTRY;
    }

    /**
     * Redis INFO MEMORY가 제공하는 서버 전체 사용 메모리 값을 반환한다.
     */
    @Override
    public long redisUsedMemoryBytes() {
        Long bytes = redisTemplate.execute((RedisCallback<Long>) connection -> {
            Properties memoryInfo = connection.serverCommands().info("memory");
            String usedMemory = memoryInfo.getProperty("used_memory");
            return usedMemory == null ? 0L : Long.parseLong(usedMemory);
        });
        return bytes == null ? 0L : bytes;
    }

    /**
     * 조회 window에 포함되는 모든 분 단위 bucket key를 만든다.
     */
    private List<String> bucketKeys(RankingWindow window, Instant now) {
        long fromBucketKey = bucketKeyValue(now.minus(window.duration()));
        long toBucketKey = bucketKeyValue(now);
        return LongStream.rangeClosed(fromBucketKey, toBucketKey)
                .mapToObj(bucketKey -> BUCKET_KEY_PREFIX + bucketKey)
                .toList();
    }

    /**
     * 이벤트 발생 시각이 속한 Redis bucket key를 만든다.
     */
    private String bucketKey(Instant instant) {
        return BUCKET_KEY_PREFIX + bucketKeyValue(instant);
    }

    /**
     * Instant를 분 단위 bucket 번호로 변환한다.
     */
    private long bucketKeyValue(Instant instant) {
        return instant.toEpochMilli() / BUCKET_SIZE.toMillis();
    }

    /**
     * Redis ZSET member 문자열을 만든다. 동점 정렬을 위해 productId 역순 값을 prefix로 둔다.
     */
    private String member(Long productId) {
        // reverseRange는 동점일 때 member 내림차순이므로 productId를 뒤집어 productId 오름차순을 유지한다.
        long tieBreaker = PRODUCT_ID_PAD_BASE - productId;
        return "%019d:%d".formatted(tieBreaker, productId);
    }

    /**
     * ZSET member 문자열에서 원래 productId를 복원한다.
     */
    private Long productId(String member) {
        return Long.valueOf(member.substring(member.indexOf(':') + 1));
    }

    /**
     * Redis가 Double로 반환한 score를 서비스에서 쓰는 long 점수로 변환한다.
     */
    private long score(Double score) {
        if (score == null) {
            return 0L;
        }
        return score.longValue();
    }
}
