package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductBoughtTogether;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBoughtTogetherRepository extends JpaRepository<ProductBoughtTogether, Long> {
}
