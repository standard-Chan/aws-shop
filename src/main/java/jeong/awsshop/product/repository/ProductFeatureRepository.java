package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductFeature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFeatureRepository extends JpaRepository<ProductFeature, Long> {
}
