package jeong.awsshop.eventpipeline.productranking.presentation.dto;

import jeong.awsshop.eventpipeline.productranking.domain.ProductRankingMemoryStats;

public record ProductRankingMemoryStatsResponse(
        long hashLength,
        long estimatedHashMemoryBytes,
        long estimatedBytesPerEntry,
        long redisUsedMemoryBytes,
        long jvmUsedMemoryBytes,
        long jvmTotalMemoryBytes,
        long jvmMaxMemoryBytes
) {

    public static ProductRankingMemoryStatsResponse from(ProductRankingMemoryStats stats) {
        return new ProductRankingMemoryStatsResponse(
                stats.hashLength(),
                stats.estimatedHashMemoryBytes(),
                stats.estimatedBytesPerEntry(),
                stats.redisUsedMemoryBytes(),
                stats.jvmUsedMemoryBytes(),
                stats.jvmTotalMemoryBytes(),
                stats.jvmMaxMemoryBytes()
        );
    }
}
