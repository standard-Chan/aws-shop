package jeong.awsshop.review.repository;

import java.util.List;
import jeong.awsshop.review.domain.ReviewImage;
import jeong.awsshop.review.repository.projection.ReviewImageProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    @Query(value = """
            SELECT
                ri.review_id AS reviewId,
                ri.small_image_url AS smallImageUrl,
                ri.medium_image_url AS mediumImageUrl,
                ri.large_image_url AS largeImageUrl,
                ri.attachment_type AS attachmentType
            FROM review_image ri
            WHERE ri.review_id IN (:reviewIds)
            ORDER BY ri.review_id ASC, ri.id ASC
            """, nativeQuery = true)
    List<ReviewImageProjection> findReviewImagesByReviewIds(@Param("reviewIds") List<Long> reviewIds);
}
