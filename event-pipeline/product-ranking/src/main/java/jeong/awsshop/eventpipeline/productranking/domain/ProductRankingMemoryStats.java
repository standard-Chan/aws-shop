package jeong.awsshop.eventpipeline.productranking.domain;

public record ProductRankingMemoryStats(
        long hashLength,
        long estimatedHashMemoryBytes,
        long estimatedBytesPerEntry,
        long redisUsedMemoryBytes,
        long jvmUsedMemoryBytes,
        long jvmTotalMemoryBytes,
        long jvmMaxMemoryBytes
) {
}
