package jeong.awsshop.product.repository;

import jeong.awsshop.product.domain.ProductVideo;
import java.util.List;
import jeong.awsshop.product.repository.projection.ProductVideoDetailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVideoRepository extends JpaRepository<ProductVideo, Long> {

    @Query(value = """
            SELECT
                pv.title AS title,
                pv.url AS url,
                pv.user_id AS userId
            FROM product_videos pv
            WHERE pv.product_id = :productId
            ORDER BY pv.id ASC
            """, nativeQuery = true)
    List<ProductVideoDetailProjection> findVideoDetailsByProductId(@Param("productId") Long productId);
}
