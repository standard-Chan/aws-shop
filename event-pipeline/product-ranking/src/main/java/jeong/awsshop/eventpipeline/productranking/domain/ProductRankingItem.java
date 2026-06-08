package jeong.awsshop.eventpipeline.productranking.domain;

public record ProductRankingItem(
        long rank,
        Long productId,
        long score
) {
}
