package jeong.awsshop.eventpipeline.productranking.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;

/**
 * Redis로 보내기 직전 batch 안의 점수 변경분을 압축한다.
 * 시간, product_id가 같은 event는 하나로 합쳐서, 데이터 수를 줄인다.
 */
class ProductRankingScoreCompressor {

    private static final Duration BUCKET_SIZE = Duration.ofMinutes(1);

    List<ProductRankingScoreDelta> compress(List<ProductRankingScoreDelta> batch) {
        if (batch.size() <= 1) {
            return batch;
        }

        Map<BucketProductKey, Long> compressedScores = new LinkedHashMap<>();
        for (ProductRankingScoreDelta delta : batch) {
            // Redis 저장 key와 동일한 1분 bucket + productId 기준으로 묶어 ZINCRBY 명령 수를 줄인다.
            BucketProductKey key = new BucketProductKey(bucketKeyValue(delta.occurredAt()), delta.productId());
            compressedScores.merge(key, delta.score(), Long::sum);
        }

        return compressedScores.entrySet().stream()
                .map(entry -> new ProductRankingScoreDelta(
                        entry.getKey().productId(),
                        entry.getValue(),
                        bucketStart(entry.getKey().bucketKey())
                ))
                .toList();
    }

    private long bucketKeyValue(Instant instant) {
        return instant.toEpochMilli() / BUCKET_SIZE.toMillis();
    }

    private Instant bucketStart(long bucketKey) {
        // 압축 후 occurredAt은 해당 Redis bucket을 다시 계산할 수 있는 bucket 시작 시각으로 정규화한다.
        return Instant.ofEpochMilli(bucketKey * BUCKET_SIZE.toMillis());
    }

    private record BucketProductKey(long bucketKey, Long productId) {
    }
}
