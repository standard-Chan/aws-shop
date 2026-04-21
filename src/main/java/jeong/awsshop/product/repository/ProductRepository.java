package jeong.awsshop.product.repository;

import java.util.List;
import java.util.Optional;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByParentAsin(String parentAsin);

    boolean existsByParentAsin(String parentAsin);

    @Query(value = """
            SELECT
                p.id AS id,
                p.parent_asin AS parentAsin,
                p.title AS title,
                p.main_category AS mainCategory,
                p.average_rating AS averageRating,
                p.rating_number AS ratingNumber,
                p.price AS price,
                p.store AS store,
                ri.variant AS imageVariant,
                ri.thumb AS imageThumb,
                ri.large AS imageLarge,
                ri.hi_res AS imageHiRes
            FROM (
                SELECT
                    p.id,
                    p.parent_asin,
                    p.title,
                    p.main_category,
                    p.average_rating,
                    p.rating_number,
                    p.price,
                    p.store
                FROM product p
                WHERE (:cursor IS NULL OR p.id > :cursor)
                ORDER BY p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN (
                SELECT
                    ranked.product_id,
                    ranked.variant,
                    ranked.thumb,
                    ranked.large,
                    ranked.hi_res
                FROM (
                    SELECT
                        pi.product_id,
                        pi.variant,
                        pi.thumb,
                        pi.large,
                        pi.hi_res,
                        ROW_NUMBER() OVER (
                            PARTITION BY pi.product_id
                            ORDER BY
                                CASE WHEN pi.variant = 'MAIN' THEN 0 ELSE 1 END,
                                pi.id ASC
                        ) AS rn
                    FROM product_images pi
                ) ranked
                WHERE ranked.rn = 1
            ) ri ON ri.product_id = p.id
            ORDER BY p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findProductSummaries(
            @Param("cursor") Long cursor,
            @Param("limit") int limit
    );
}
