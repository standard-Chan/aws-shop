package jeong.awsshop.eventpipeline.productranking.domain;

import java.time.Instant;

public record ProductRankingScoreDelta(
        Long productId,
        long score,
        Instant occurredAt
) {
}
