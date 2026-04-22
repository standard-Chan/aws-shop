package jeong.awsshop.product.repository.projection;

public interface ProductBoughtTogetherDetailProjection {

    Long getRelatedProductId();

    String getRelatedProductTitle();

    String getRelatedProductImageUrl();
}
