package jeong.awsshop.product.service.search.document;

public record ProductSearchImageDocument(
        String variant,
        String thumb,
        String large,
        String hiRes
) {
}
