package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductImage;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductImageDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query(value = """
            SELECT
                pi.variant AS variant,
                pi.thumb AS thumb,
                pi.large AS large,
                pi.hi_res AS hiRes
            FROM product_images pi
            WHERE pi.product_id = :productId
            ORDER BY
                CASE WHEN pi.variant = 'MAIN' THEN 0 ELSE 1 END,
                pi.id ASC
            """, nativeQuery = true)
    List<ProductImageDetailProjection> findImageDetailsByProductId(@Param("productId") Long productId);
}
