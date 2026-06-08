package jeong.awsshop.eventpipeline.productranking.domain;

import java.util.List;

public interface ProductRankingStore {

    void increaseScore(Long productId, long score);

    List<ProductRankingItem> findTop(int limit);

    long hashLength();

    long estimatedHashMemoryBytes();

    long estimatedBytesPerEntry();
}
