package jeong.awsshop.eventpipeline.productranking.application;

import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingScoreDelta;

public interface ProductRankingScoreWriter {

    void save(ProductRankingScoreDelta delta);
}
