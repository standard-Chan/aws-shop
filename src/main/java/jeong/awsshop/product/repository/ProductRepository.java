package jeong.awsshop.product.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    String KEYWORD_CONTAINS_CLAUSE = """
            
            LOWER(p.title) LIKE CONCAT(
                '%',
                REPLACE(
                    REPLACE(
                        REPLACE(LOWER(:keyword), '#', '##'),
                        '%', '#%'
                    ),
                    '_', '#_'
                ),
                '%'
            ) ESCAPE '#'
            
            """;

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
                p.details AS details
            FROM product p
            WHERE p.id = :id
            """, nativeQuery = true)
    Optional<ProductDetailProjection> findDetailById(@Param("id") Long id);

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findProductSummaries(
            @Param("cursor") Long cursorId,
            @Param("limit") int limit
    );

    boolean existsByIdAndMainCategory(Long id, String mainCategory);

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE p.main_category = :mainCategory
                  AND p.average_rating IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.average_rating < :cursorAverageRating
                      OR (p.average_rating = :cursorAverageRating AND p.id > :cursorId)
                  )
                ORDER BY p.average_rating DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.average_rating DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByAverageRating(
            @Param("mainCategory") String mainCategory,
            @Param("cursorId") Long cursorId,
            @Param("cursorAverageRating") BigDecimal cursorAverageRating,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE p.main_category = :mainCategory
                  AND p.rating_number IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.rating_number < :cursorRatingNumber
                      OR (p.rating_number = :cursorRatingNumber AND p.id > :cursorId)
                  )
                ORDER BY p.rating_number DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.rating_number DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByRatingNumber(
            @Param("mainCategory") String mainCategory,
            @Param("cursorId") Long cursorId,
            @Param("cursorRatingNumber") Integer cursorRatingNumber,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE p.main_category = :mainCategory
                  AND p.price IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.price > :cursorPrice
                      OR (p.price = :cursorPrice AND p.id > :cursorId)
                  )
                ORDER BY p.price ASC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.price ASC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByPriceAsc(
            @Param("mainCategory") String mainCategory,
            @Param("cursorId") Long cursorId,
            @Param("cursorPrice") BigDecimal cursorPrice,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE p.main_category = :mainCategory
                  AND p.price IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.price < :cursorPrice
                      OR (p.price = :cursorPrice AND p.id > :cursorId)
                  )
                ORDER BY p.price DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.price DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findCategoryProductSummariesOrderByPriceDesc(
            @Param("mainCategory") String mainCategory,
            @Param("cursorId") Long cursorId,
            @Param("cursorPrice") BigDecimal cursorPrice,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE """ + KEYWORD_CONTAINS_CLAUSE + """
                  AND p.average_rating IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.average_rating < :cursorAverageRating
                      OR (p.average_rating = :cursorAverageRating AND p.id > :cursorId)
                  )
                ORDER BY p.average_rating DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.average_rating DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findKeywordProductSummariesOrderByAverageRating(
            @Param("keyword") String keyword,
            @Param("cursorId") Long cursorId,
            @Param("cursorAverageRating") BigDecimal cursorAverageRating,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE """ + KEYWORD_CONTAINS_CLAUSE + """
                  AND p.rating_number IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.rating_number < :cursorRatingNumber
                      OR (p.rating_number = :cursorRatingNumber AND p.id > :cursorId)
                  )
                ORDER BY p.rating_number DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.rating_number DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findKeywordProductSummariesOrderByRatingNumber(
            @Param("keyword") String keyword,
            @Param("cursorId") Long cursorId,
            @Param("cursorRatingNumber") Integer cursorRatingNumber,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE """ + KEYWORD_CONTAINS_CLAUSE + """
                  AND p.price IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.price > :cursorPrice
                      OR (p.price = :cursorPrice AND p.id > :cursorId)
                  )
                ORDER BY p.price ASC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.price ASC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findKeywordProductSummariesOrderByPriceAsc(
            @Param("keyword") String keyword,
            @Param("cursorId") Long cursorId,
            @Param("cursorPrice") BigDecimal cursorPrice,
            @Param("limit") int limit
    );

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
                pi.variant AS imageVariant,
                pi.thumb AS imageThumb,
                pi.large AS imageLarge,
                pi.hi_res AS imageHiRes
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
                WHERE """ + KEYWORD_CONTAINS_CLAUSE + """
                  AND p.price IS NOT NULL
                  AND (
                      :cursorId IS NULL
                      OR p.price < :cursorPrice
                      OR (p.price = :cursorPrice AND p.id > :cursorId)
                  )
                ORDER BY p.price DESC, p.id ASC
                LIMIT :limit
            ) p
            LEFT JOIN product_images pi
              ON pi.id = (
                  SELECT pi2.id
                  FROM product_images pi2
                  WHERE pi2.product_id = p.id
                  ORDER BY
                      CASE WHEN pi2.variant = 'MAIN' THEN 0 ELSE 1 END,
                      pi2.id ASC
                  LIMIT 1
              )
            ORDER BY p.price DESC, p.id ASC
            """, nativeQuery = true)
    List<ProductSummaryNativeProjection> findKeywordProductSummariesOrderByPriceDesc(
            @Param("keyword") String keyword,
            @Param("cursorId") Long cursorId,
            @Param("cursorPrice") BigDecimal cursorPrice,
            @Param("limit") int limit
    );
}
