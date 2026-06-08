package jeong.awsshop.eventpipeline.productranking.infrastructure;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;
import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingStore;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProductRankingStore implements ProductRankingStore {

    private final ConcurrentHashMap<Long, LongAdder> scores = new ConcurrentHashMap<>();

    @Override
    public void increaseScore(Long productId, long score) {
        scores.computeIfAbsent(productId, ignored -> new LongAdder()).add(score);
    }

    @Override
    public List<ProductRankingItem> findTop(int limit) {
        List<RankedScore> rankedScores = scores.entrySet().stream()
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

    private record RankedScore(Long productId, long score) {
    }
}
