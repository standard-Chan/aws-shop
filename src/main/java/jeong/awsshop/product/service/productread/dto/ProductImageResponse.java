package jeong.awsshop.product.service.productread.dto;

public record ProductImageResponse(
        String variant,
        String thumb,
        String large,
        String hiRes
) {
}
