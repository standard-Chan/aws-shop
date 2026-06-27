package jeong.awsshop.eventpipeline.productranking.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import jeong.awsshop.eventpipeline.productranking.domain.RankingWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Deprecated(since = "1.2.1", forRemoval = false)
@Repository
@ConditionalOnProperty(
        prefix = "event-pipeline.product-ranking.redis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class InMemoryProductRankingStore implements ProductRankingStore {

    // Redis를 끈 로컬/부하테스트용 fallback이다.
    private static final long ESTIMATED_BYTES_PER_ENTRY = 128L;
    private static final Duration BUCKET_SIZE = Duration.ofMinutes(1);
    private static final Duration RETENTION = Duration.ofDays(7);

    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, LongAdder>> buckets = new ConcurrentHashMap<>();

    @Override
    public void increaseScore(Long productId, long score, Instant occurredAt) {
        long bucketKey = bucketKey(occurredAt);
        buckets.computeIfAbsent(bucketKey, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(productId, ignored -> new LongAdder())
                .add(score);
    }

    @Override
    public List<ProductRankingItem> findTop(RankingWindow window, int limit, Instant now) {
        pruneExpiredBuckets(now);

        long fromBucketKey = bucketKey(now.minus(window.duration()));
        long toBucketKey = bucketKey(now);
        Map<Long, LongAdder> mergedScores = new ConcurrentHashMap<>();

        buckets.entrySet().stream()
                .filter(entry -> entry.getKey() >= fromBucketKey)
                .filter(entry -> entry.getKey() <= toBucketKey)
                .flatMap(entry -> entry.getValue().entrySet().stream())
                .forEach(entry -> mergedScores
                        .computeIfAbsent(entry.getKey(), ignored -> new LongAdder())
                        .add(entry.getValue().sum()));

        List<RankedScore> rankedScores = mergedScores.entrySet().stream()
                .map(entry -> new RankedScore(entry.getKey(), entry.getValue().sum()))
                .sorted(Comparator
                        .comparingLong(RankedScore::score).reversed()
                        .thenComparing(RankedScore::productId))
                .limit(limit)
                .toList();

        return IntStream.range(0, rankedScores.size())
                .mapToObj(index -> new ProductRankingItem(
                        index + 1L,
                        rankedScores.get(index).productId(),
                        rankedScores.get(index).score()
                ))
                .toList();
    }

    @Override
    public long hashLength() {
        return buckets.values().stream()
                .mapToLong(ConcurrentHashMap::mappingCount)
                .sum();
    }

    @Override
    public long estimatedHashMemoryBytes() {
        return hashLength() * ESTIMATED_BYTES_PER_ENTRY;
    }

    @Override
    public long estimatedBytesPerEntry() {
        return ESTIMATED_BYTES_PER_ENTRY;
    }

    @Override
    public long redisUsedMemoryBytes() {
        return 0L;
    }

    private void pruneExpiredBuckets(Instant now) {
        long oldestBucketKey = bucketKey(now.minus(RETENTION));
        buckets.keySet().removeIf(bucketKey -> bucketKey < oldestBucketKey);
    }

    private long bucketKey(Instant instant) {
        return instant.toEpochMilli() / BUCKET_SIZE.toMillis();
    }

    private record RankedScore(Long productId, long score) {
    }
}
