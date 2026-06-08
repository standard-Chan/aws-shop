package jeong.awsshop.eventpipeline.productranking.domain;

import java.time.Instant;
import java.util.List;

public interface ProductRankingStore {

    void increaseScore(Long productId, long score, Instant occurredAt);

    List<ProductRankingItem> findTop(RankingWindow window, int limit, Instant now);

    long hashLength();

    long estimatedHashMemoryBytes();

    long estimatedBytesPerEntry();
}
