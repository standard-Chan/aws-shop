package jeong.awsshop.product.repository;

import java.util.Optional;
import jeong.awsshop.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByParentAsin(String parentAsin);

    boolean existsByParentAsin(String parentAsin);
}
