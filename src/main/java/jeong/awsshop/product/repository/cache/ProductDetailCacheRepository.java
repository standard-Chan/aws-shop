package jeong.awsshop.product.repository.cache;

import java.util.Optional;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;

public interface ProductDetailCacheRepository {

    Optional<ProductDetailResponse> findByProductId(Long productId);

    void save(Long productId, ProductDetailResponse response);
}
