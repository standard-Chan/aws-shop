package jeong.awsshop.review.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.review.dto.ReviewDto;
import jeong.awsshop.review.dto.ReviewImageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ReviewBulkInsertRepository {

    private final DataSource dataSource;
    private final SnowflakeIdGenerator idGenerator;

    public List<ReviewDto> bulkInsert(List<ReviewDto> reviews) {
        String reviewSql = """
            INSERT INTO review (
                id, user_id, product_id, asin, rating, title, text, timestamp, verified_purchase, helpful_vote
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String imageSql = """
            INSERT INTO review_image (
                id, review_id, small_image_url, medium_image_url, large_image_url, attachment_type
            ) VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (
                    PreparedStatement reviewPs = connection.prepareStatement(reviewSql);
                    PreparedStatement imagePs = connection.prepareStatement(imageSql)
            ) {
                for (ReviewDto dto : reviews) {
                    long reviewId = idGenerator.nextId();

                    reviewPs.setLong(1, reviewId);
                    reviewPs.setString(2, dto.userId());
                    reviewPs.setString(3, dto.parentAsin());
                    reviewPs.setString(4, dto.asin());
                    reviewPs.setFloat(5, dto.rating());
                    reviewPs.setString(6, dto.title());
                    reviewPs.setString(7, dto.text());
                    reviewPs.setLong(8, dto.timestamp());
                    reviewPs.setBoolean(9, dto.verifiedPurchase());
                    reviewPs.setInt(10, dto.helpfulVote());
                    reviewPs.addBatch();

                    for (ReviewImageDto image : dto.imagesOrEmpty()) {
                        imagePs.setLong(1, idGenerator.nextId());
                        imagePs.setLong(2, reviewId);
                        imagePs.setString(3, image.smallImageUrl());
                        imagePs.setString(4, image.mediumImageUrl());
                        imagePs.setString(5, image.largeImageUrl());
                        imagePs.setString(6, image.attachmentType());
                        imagePs.addBatch();
                    }
                }

                reviewPs.executeBatch();
                imagePs.executeBatch();
                connection.commit();
                return new ArrayList<>();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("[Review Bulk Insert 실패]: batch insert 중 오류가 발생했습니다.", e);
            return reviews;
        }
    }
}
