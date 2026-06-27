package jeong.awsshop.product.service.search.dto;

public record ProductSearchReindexResponse(
        long indexedCount,
        long failedCount,
        long elapsedMillis
) {
}
