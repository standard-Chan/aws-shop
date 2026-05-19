package jeong.awsshop.review.repository;

import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    /**
     * sort와 direction 조합에 맞는 최소 조회 쿼리를 실행한다.
     */
    @Override
    public List<ReviewSummaryProjection> findReviewSummaries(
            String parentAsin,
            String sort,
            String direction,
            Long cursorId,
            Long cursorTimestamp,
            Integer cursorHelpfulVote,
            Float cursorRating,
            Integer limit
    ) {
        String normalizedSort = normalizeSort(sort);
        String normalizedDirection = normalizeDirection(direction);

        String sortColumn = "rating".equals(normalizedSort) ? "rating" : "helpful_vote";
        String primaryOrder = "asc".equals(normalizedDirection) ? "ASC" : "DESC";
        StringBuilder sql = new StringBuilder("""
                SELECT
                    r.id AS id,
                    r.rating AS rating,
                    r.title AS title,
                    r.text AS text,
                    r.timestamp AS timestamp,
                    r.user_id AS userId,
                    r.verified_purchase AS verifiedPurchase,
                    r.helpful_vote AS helpfulVote,
                    r.asin AS asin,
                    r.product_id AS parentAsin
                FROM review r
                WHERE r.product_id = ?
                  AND r.%s IS NOT NULL
                ORDER BY r.%s %s, r.timestamp DESC, r.id ASC
                LIMIT ?
                """.formatted(
                sortColumn, sortColumn, primaryOrder
        ));

        List<Object> params = new ArrayList<>();
        params.add(parentAsin);

        if (cursorId != null) {
            Object cursorValue = "rating".equals(normalizedSort) ? cursorRating : cursorHelpfulVote;
            sql.insert(sql.indexOf("ORDER BY"), """
                      AND (
                          %s
                      )
                    """.formatted(buildCursorPredicate(sortColumn, normalizedDirection)));
            params.add(cursorValue);
            params.add(cursorValue);
            params.add(cursorTimestamp);
            params.add(cursorValue);
            params.add(cursorTimestamp);
            params.add(cursorId);
        }
        params.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                (rs, rowNum) -> new ReviewSummaryRow(
                        rs.getLong("id"),
                        rs.getObject("rating", Float.class),
                        rs.getString("title"),
                        rs.getString("text"),
                        rs.getLong("timestamp"),
                        rs.getString("userId"),
                        rs.getObject("verifiedPurchase", Boolean.class),
                        rs.getObject("helpfulVote", Integer.class),
                        rs.getString("asin"),
                        rs.getString("parentAsin")
                )
        );
    }

    private String normalizeSort(String sort) {
        return "rating".equalsIgnoreCase(sort) ? "rating" : "helpfulVote";
    }

    private String normalizeDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
    }

    /**
     * 기본 tie-breaker인 timestamp DESC, id ASC를 유지한 cursor 조건을 만든다.
     */
    private String buildCursorPredicate(String sortColumn, String direction) {
        String comparison = "asc".equals(direction) ? ">" : "<";
        return """
                r.%s %s ?
                OR (r.%s = ? AND r.timestamp < ?)
                OR (r.%s = ? AND r.timestamp = ? AND r.id > ?)
                """.formatted(sortColumn, comparison, sortColumn, sortColumn);
    }

    private static final class ReviewSummaryRow implements ReviewSummaryProjection {
        private final Long id;
        private final Float rating;
        private final String title;
        private final String text;
        private final Long timestamp;
        private final String userId;
        private final Boolean verifiedPurchase;
        private final Integer helpfulVote;
        private final String asin;
        private final String parentAsin;

        private ReviewSummaryRow(
                Long id,
                Float rating,
                String title,
                String text,
                Long timestamp,
                String userId,
                Boolean verifiedPurchase,
                Integer helpfulVote,
                String asin,
                String parentAsin
        ) {
            this.id = id;
            this.rating = rating;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
            this.userId = userId;
            this.verifiedPurchase = verifiedPurchase;
            this.helpfulVote = helpfulVote;
            this.asin = asin;
            this.parentAsin = parentAsin;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Float getRating() {
            return rating;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public Boolean getVerifiedPurchase() {
            return verifiedPurchase;
        }

        @Override
        public Integer getHelpfulVote() {
            return helpfulVote;
        }

        @Override
        public String getAsin() {
            return asin;
        }

        @Override
        public String getParentAsin() {
            return parentAsin;
        }
    }
}
