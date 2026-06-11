package jeong.awsshop.eventpipeline.productranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRankingScoreCompressorTest {

    private final ProductRankingScoreCompressor compressor = new ProductRankingScoreCompressor();

    @Test
    @DisplayName("같은 분 bucket과 productId의 점수를 하나로 합산해야 한다")
    void should_compress_scores_by_bucket_and_product_id() {
        Instant bucketTime = Instant.parse("2026-06-07T06:00:10Z");

        List<ProductRankingScoreDelta> compressed = compressor.compress(List.of(
                delta(100L, 1L, bucketTime),
                delta(100L, 3L, Instant.parse("2026-06-07T06:00:59Z")),
                delta(200L, 10L, bucketTime),
                delta(100L, 10L, Instant.parse("2026-06-07T06:01:00Z"))
        ));

        assertThat(compressed)
                .extracting("productId", "score", "occurredAt")
                .containsExactly(
                        tuple(100L, 4L, Instant.parse("2026-06-07T06:00:00Z")),
                        tuple(200L, 10L, Instant.parse("2026-06-07T06:00:00Z")),
                        tuple(100L, 10L, Instant.parse("2026-06-07T06:01:00Z"))
                );
    }

    private ProductRankingScoreDelta delta(Long productId, long score, Instant occurredAt) {
        return new ProductRankingScoreDelta(productId, score, occurredAt);
    }
}
