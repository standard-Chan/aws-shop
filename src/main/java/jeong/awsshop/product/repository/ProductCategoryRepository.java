package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductCategory;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductCategoryDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    @Query(value = """
            SELECT pc.category AS category
            FROM product_categories pc
            WHERE pc.product_id = :productId
            ORDER BY pc.id ASC
            """, nativeQuery = true)
    List<ProductCategoryDetailProjection> findCategoryDetailsByProductId(@Param("productId") Long productId);
}
