package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductDescription;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductDescriptionDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDescriptionRepository extends JpaRepository<ProductDescription, Long> {

    @Query(value = """
            SELECT
                pd.description_index AS descriptionIndex,
                pd.description AS description
            FROM product_descriptions pd
            WHERE pd.product_id = :productId
            ORDER BY
                CASE WHEN pd.description_index IS NULL THEN 1 ELSE 0 END,
                pd.description_index ASC,
                pd.id ASC
            """, nativeQuery = true)
    List<ProductDescriptionDetailProjection> findDescriptionDetailsByProductId(@Param("productId") Long productId);
}
