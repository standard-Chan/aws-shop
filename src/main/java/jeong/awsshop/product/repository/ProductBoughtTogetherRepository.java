package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductBoughtTogether;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductBoughtTogetherDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBoughtTogetherRepository extends JpaRepository<ProductBoughtTogether, Long> {

    @Query(value = """
            SELECT
                pbt.related_product_id AS relatedProductId,
                pbt.related_product_title AS relatedProductTitle,
                pbt.related_product_image_url AS relatedProductImageUrl
            FROM product_bought_together pbt
            WHERE pbt.product_id = :productId
            ORDER BY pbt.id ASC
            """, nativeQuery = true)
    List<ProductBoughtTogetherDetailProjection> findBoughtTogetherDetailsByProductId(
            @Param("productId") Long productId
    );
}
