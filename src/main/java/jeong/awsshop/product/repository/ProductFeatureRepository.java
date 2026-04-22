package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductFeature;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductFeatureDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductFeatureRepository extends JpaRepository<ProductFeature, Long> {

    @Query(value = """
            SELECT
                pf.feature_index AS featureIndex,
                pf.feature AS feature
            FROM product_features pf
            WHERE pf.product_id = :productId
            ORDER BY
                CASE WHEN pf.feature_index IS NULL THEN 1 ELSE 0 END,
                pf.feature_index ASC,
                pf.id ASC
            """, nativeQuery = true)
    List<ProductFeatureDetailProjection> findFeatureDetailsByProductId(@Param("productId") Long productId);
}
