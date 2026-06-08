package jeong.awsshop.eventpipeline.productranking.presentation.dto;

import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingItem;

public record ProductRankingResponse(
        long rank,
        Long productId,
        long score
) {

    public static ProductRankingResponse from(ProductRankingItem item) {
        return new ProductRankingResponse(item.rank(), item.productId(), item.score());
    }
}
