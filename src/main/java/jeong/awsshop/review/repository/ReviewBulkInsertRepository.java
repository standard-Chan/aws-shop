package jeong.awsshop.review.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
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

    private static final String REVIEW_INSERT_SQL = """
        INSERT INTO review (
            id, user_id, product_id, asin, rating, title, text, timestamp, verified_purchase, helpful_vote
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private static final String REVIEW_IMAGE_INSERT_SQL = """
        INSERT INTO review_image (
            id, review_id, small_image_url, medium_image_url, large_image_url, attachment_type
        ) VALUES (?, ?, ?, ?, ?, ?)
    """;

    private final DataSource dataSource;
    private final SnowflakeIdGenerator idGenerator;

    public List<ReviewDto> bulkInsert(List<ReviewDto> reviews) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (
                    PreparedStatement reviewPs = connection.prepareStatement(REVIEW_INSERT_SQL);
                    PreparedStatement imagePs = connection.prepareStatement(REVIEW_IMAGE_INSERT_SQL)
            ) {
                for (ReviewDto dto : reviews) {
                    long reviewId = idGenerator.nextId();

                    addReviewBatch(reviewPs, dto, reviewId);

                    for (ReviewImageDto image : dto.imagesOrEmpty()) {
                        addReviewImageBatch(imagePs, image, reviewId);
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
        } catch (SQLException e) {
            // MySQL 유니크 키 등 제약조건 위반인 경우 (중복 데이터가 많아, 제외가 필요)
            if (e instanceof SQLIntegrityConstraintViolationException
                || e.getErrorCode() == 1062) {
                log.info("[Bulk Insert] 유니크 키가 중복되어 insert 를 skip합니다.");
                return new ArrayList<>();
            }

            // 그 외 SQL 예외는 로그를 남기고 실패한 batch를 반환하여 재처리할 수 있도록 한다
            log.error("[Review Bulk Insert 실패]: 데이터베이스 오류가 발생했습니다.", e);
            return reviews;
        } catch (Exception e) {
            log.error("[Review Bulk Insert 실패]: batch insert 중 오류가 발생했습니다.", e);
            return reviews;
        }
    }

    private void addReviewBatch(PreparedStatement reviewPs, ReviewDto dto, long reviewId) throws SQLException {
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
    }

    private void addReviewImageBatch(PreparedStatement imagePs, ReviewImageDto image, long reviewId)
            throws SQLException {
        imagePs.setLong(1, idGenerator.nextId());
        imagePs.setLong(2, reviewId);
        imagePs.setString(3, image.smallImageUrl());
        imagePs.setString(4, image.mediumImageUrl());
        imagePs.setString(5, image.largeImageUrl());
        imagePs.setString(6, image.attachmentType());
        imagePs.addBatch();
    }
}
