package jeong.awsshop.eventpipeline.productranking.domain;

import java.time.Instant;
import java.util.List;

/**
 * 상품 랭킹 점수를 저장하고 시간 window 기준 상위 상품을 조회하는 저장소 추상화다.
 * 구현체는 in-memory, Redis처럼 저장 방식만 바꾸고 service 계약은 유지한다.
 */
public interface ProductRankingStore {

    /**
     * 특정 시각에 발생한 상품 이벤트 점수를 누적한다.
     */
    void increaseScore(Long productId, long score, Instant occurredAt);

    /**
     * 여러 상품 이벤트 점수를 batch 단위로 누적한다.
     * 기존 구현체는 단건 저장 반복으로 호환하고, Redis 구현체는 pipeline으로 최적화한다.
     */
    default void increaseScores(List<ProductRankingScoreDelta> deltas) {
        for (ProductRankingScoreDelta delta : deltas) {
            increaseScore(delta.productId(), delta.score(), delta.occurredAt());
        }
    }

    /**
     * 현재 시각 기준 window 안에서 점수가 높은 상품을 최대 limit개까지 조회한다.
     */
    List<ProductRankingItem> findTop(RankingWindow window, int limit, Instant now);

    /**
     * 랭킹 저장소에 들어 있는 상품 점수 entry 수를 반환한다.
     */
    long hashLength();

    /**
     * 저장소 entry 수를 기준으로 메모리 사용량을 추정한다.
     */
    long estimatedHashMemoryBytes();

    /**
     * 저장소 entry 1개의 추정 메모리 크기를 반환한다.
     */
    long estimatedBytesPerEntry();

    /**
     * Redis 서버가 보고하는 전체 사용 메모리를 반환한다. Redis가 아닌 구현체는 0을 반환한다.
     */
    long redisUsedMemoryBytes();
}
