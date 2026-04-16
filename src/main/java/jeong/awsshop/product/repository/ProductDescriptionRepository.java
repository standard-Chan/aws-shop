package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductDescription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductDescriptionRepository extends JpaRepository<ProductDescription, Long> {
}
