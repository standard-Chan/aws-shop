package jeong.awsshop.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.review.repository.projection.ReviewImageProjection;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewRepositoryCursorQueryTest {

    private static final String TARGET_PARENT_ASIN = "B096MTTDJL";

    private static final String REAL_REVIEW_JSONL = """
            {"parent_asin":"B07XGNHYC2","user_id":"AEHMP5OY6IDM7TR3QXEM4H4RT4YA","helpful_vote":0,"verified_purchase":true,"rating":4.0,"title":"Yummy 😋","text":"It’s worth it!","images":[],"asin":"B07XKF8CB3","timestamp":1610649371942}
            {"parent_asin":"B094VMFRW3","user_id":"AG3J3I2L64JNO2DOA3SPOQYNSBAA","helpful_vote":22,"verified_purchase":false,"rating":1.0,"title":"I LOVE South Park. That should tell you how bad this has been.","text":"I have returned EVERY SINGLE shirt I have received so far.<br /><br />The first one?<br /><br />Piggy Cartmans Glasses on a gray shirt. Just the glasses.<br /><br />Second One?<br /><br />A member Berry. Completely out of focus and looked like a big, warped purple circle on a charcoal-esque colored shirt.<br /><br />The one on the way?<br /><br />It’s black and says “Cartman Burger” using the same handwriting from the corresponding episode(s) with the drawing of a hamburger<br /><br />If ANY of that sounds appealing to you, then by all means, go for it.<br /><br />Personally, I’m going to return the Cartman Burger shirt when it gets here Monday and then if shirt #4 is crapfest like the first 3 have been, then it’s getting cancelled.<br /><br />The ONLY reason I haven’t cancelled already is because I LOVE South Park. It is my favorite show (seasons 1-18 anyway…19-present, not so much) and I had high hopes for this subscription. I REALLY wanted to like it!<br /><br />One more dud and I’m gone though.<br /><br />I’ll just buy the shirts I want. I’ve been returning these for credit and then picking out one that I want anyway.<br /><br />May as well just treat myself to a South Park shirt that I choose every month rather than being disappointed EVERY SINGLE TIME.","images":[],"asin":"B094VMR49D","timestamp":1636145385971}
            {"parent_asin":"B096MTTDJL","user_id":"AEUK73LJSRJOTGMKYNAC3CYHOX2Q","helpful_vote":11,"verified_purchase":true,"rating":5.0,"title":"I absolutely love the colors & product. However , the price is too high","text":"I am always on the hunt for new lip gloss and lipstick . I decided to try this one on an Amazon daily deal. I loved the packaging , the presentation and the adorable blinged out handles ! The colors were perfect for my skin tone and I didn't feel over done. I don't have much time these days for a full face of makeup , but these lip colors always make me feel better ! It's not sticky , doesn't rub off on everything , stays on long without having to excessively reapply it. I was hooked ! However, when it went back to purchase more they were over $20 with tax and required a subscription :( With everything going on in the economy I just couldn't justify it for 2 lipglosses . For many this is a steal, but for those on a very restricted income, its not as feasible. If the price points changes or I don't have to subscribe to monthly payments I will definitely be a customer for life !","images":[{"small_image_url":"https://m.media-amazon.com/images/I/71cGJgj94oL._SL256_.jpg","medium_image_url":"https://m.media-amazon.com/images/I/71cGJgj94oL._SL800_.jpg","large_image_url":"https://m.media-amazon.com/images/I/71cGJgj94oL._SL1600_.jpg","attachment_type":"IMAGE"},{"small_image_url":"https://m.media-amazon.com/images/I/81HFhuFUScL._SL256_.jpg","medium_image_url":"https://m.media-amazon.com/images/I/81HFhuFUScL._SL800_.jpg","large_image_url":"https://m.media-amazon.com/images/I/81HFhuFUScL._SL1600_.jpg","attachment_type":"IMAGE"}],"asin":"B096MS17VY","timestamp":1653846936825}
            {"parent_asin":"B07THJYT4Q","user_id":"AFO6SFKCN6CZT5LAU32ZTT7YY2TQ","helpful_vote":12,"verified_purchase":true,"rating":2.0,"title":"Expensive free samples","text":"The snacks are like free samples it’s not worth 28.00 that’s like 4.00 per snack it would be cheaper to just buy I only paid 14.00 for my first time it’s worth that but not 28.00","images":[],"asin":"B07THJZVK1","timestamp":1595212148230}
            {"parent_asin":"B07DVXFP2N","user_id":"AEXE5QRZ3UQJILKILGMY4ZYVMQGA","helpful_vote":2,"verified_purchase":true,"rating":4.0,"title":"A Little Expensive ?","text":"The Quality Is Perfect along with the Taste the packages are a little Small for the Price you'll be paying after the  First price will be excessive afterwards?","images":[],"asin":"B07DVXFP2N","timestamp":1594165725605}
            {"parent_asin":"B07F954281","user_id":"AH2XTH3V2CG3WXNJXDPBQJZKHIIA","helpful_vote":1,"verified_purchase":true,"rating":5.0,"title":"Tasty Rich Robust and Stout Tea 4 ALL SEASONS","text":"It has been a while since I ordered tea. I remember receiving it. Oh, what a Glorious Day it turned Out To B...","images":[],"asin":"B07F8Y6BH9","timestamp":1542240787479}
            {"parent_asin":"B07L3TPFMK","user_id":"AH333BXBCH22N2HHEUOA26IQBUTQ","helpful_vote":0,"verified_purchase":true,"rating":5.0,"title":"My 5 year old twin great grand sons love them","text":"My twin great grand twin Boys are high level kindergarten.  They get bored easily and these magazines keep them busy and interested in learning. Will continue to give them the subscription.","images":[],"asin":"B07L3K8S42","timestamp":1678583439383}
            {"parent_asin":"B08J4G2W8T","user_id":"AFFECFS2H3H5UYTIQAISHLNQP3OA","helpful_vote":1,"verified_purchase":true,"rating":1.0,"title":"Not worth it.","text":"Lame.  And it took FOREVER to get here.  Not worth the money. I didn't realize it was some ridiculous package like it is.","images":[],"asin":"B08J4G2W8T","timestamp":1614607538021}
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @BeforeAll
    void setUpFixture() throws Exception {
        // Given: 실제 review JSONL 8건을 기준 fixture로 저장한다.
        insertRealFixtureRows();

        // Given: 한 상품에 대한 cursor, tie-breaker, null 제외 검증용 row를 추가한다.
        insertAdditionalRow(20001L, TARGET_PARENT_ASIN, "fixture-user-20001", 11, true, 5.0F, "actual-row", "actual-row", "B096MS17VY", 1653846936825L);
        insertReviewImage(30001L, 20001L, "https://m.media-amazon.com/images/I/71cGJgj94oL._SL256_.jpg", "https://m.media-amazon.com/images/I/71cGJgj94oL._SL800_.jpg", "https://m.media-amazon.com/images/I/71cGJgj94oL._SL1600_.jpg", "IMAGE");
        insertReviewImage(30002L, 20001L, "https://m.media-amazon.com/images/I/81HFhuFUScL._SL256_.jpg", "https://m.media-amazon.com/images/I/81HFhuFUScL._SL800_.jpg", "https://m.media-amazon.com/images/I/81HFhuFUScL._SL1600_.jpg", "IMAGE");
        insertAdditionalRow(20002L, TARGET_PARENT_ASIN, "fixture-user-20002", 12, true, 2.0F, "tie-break-1", "tie-break-1", "B096MORE20002", 1653846937000L);
        insertAdditionalRow(20003L, TARGET_PARENT_ASIN, "fixture-user-20003", 11, true, 5.0F, "tie-break-2", "tie-break-2", "B096MORE20003", 1653846936825L);
        insertAdditionalRow(20004L, TARGET_PARENT_ASIN, "fixture-user-20004", 11, true, 4.0F, "tie-break-3", "tie-break-3", "B096MORE20004", 1653846936800L);
        insertAdditionalRow(20005L, TARGET_PARENT_ASIN, "fixture-user-20005", null, true, 4.0F, "null-helpful", "null-helpful", "B096MORE20005", 1653846936500L);
        insertAdditionalRow(20006L, TARGET_PARENT_ASIN, "fixture-user-20006", 3, true, null, "null-rating", "null-rating", "B096MORE20006", 1653846936400L);
        insertAdditionalRow(20007L, TARGET_PARENT_ASIN, "fixture-user-20007", 1, true, 5.0F, "rating-tie", "rating-tie", "B096MORE20007", 1653846936900L);
    }

    @Test
    @DisplayName("parentAsin이 일치하는 리뷰만 조회해야 한다")
    void should_find_reviews_by_parent_asin_when_parent_asin_matches() {
        // Given: 여러 parentAsin이 저장된 fixture 데이터다.

        // When: 특정 parentAsin으로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: 모든 결과는 요청 parentAsin과 일치해야 한다.
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getParentAsin()).isEqualTo(TARGET_PARENT_ASIN)
        );
    }

    @Test
    @DisplayName("helpfulVote 정렬이면 helpfulVote가 null인 리뷰를 제외해야 한다")
    void should_exclude_reviews_with_null_helpful_vote_when_sort_is_helpful_vote() {
        // Given: helpfulVote null row가 포함된 fixture 데이터다.

        // When: helpfulVote 정렬로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: helpfulVote null row id 20005는 결과에 포함되면 안 된다.
        assertThat(rows).extracting(ReviewSummaryProjection::getId)
                .doesNotContain(20005L);
    }

    @Test
    @DisplayName("기본 helpfulVote 정렬은 helpfulVote DESC, timestamp DESC, id ASC를 만족해야 한다")
    void should_sort_by_helpful_vote_desc_then_timestamp_desc_then_id_asc_when_default_sort_is_used() {
        // Given: 동일 helpfulVote와 timestamp를 가진 tie-breaker fixture 데이터다.

        // When: 기본 helpfulVote DESC로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: helpfulVote DESC, timestamp DESC, id ASC 순서를 유지해야 한다.
        assertThat(rows).extracting(ReviewSummaryProjection::getId)
                .containsSequence(20002L, 20001L, 20003L, 20004L, 20007L, 20006L);
    }

    @Test
    @DisplayName("rating 정렬이면 rating이 null인 리뷰를 제외해야 한다")
    void should_exclude_reviews_with_null_rating_when_sort_is_rating() {
        // Given: rating null row가 포함된 fixture 데이터다.

        // When: rating 정렬로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "rating",
                "asc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: rating null row id 20006은 결과에 포함되면 안 된다.
        assertThat(rows).extracting(ReviewSummaryProjection::getId)
                .doesNotContain(20006L);
    }

    @Test
    @DisplayName("rating ASC 정렬은 rating ASC와 tie-breaker를 만족해야 한다")
    void should_sort_by_rating_asc_then_timestamp_desc_then_id_asc_when_rating_asc_is_used() {
        // Given: 동일 rating을 가진 여러 row가 준비되어 있다.

        // When: rating ASC로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "rating",
                "asc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: rating 오름차순과 timestamp DESC, id ASC tie-breaker를 만족해야 한다.
        assertThat(rows).extracting(ReviewSummaryProjection::getId)
                .containsSequence(20002L, 20004L, 20007L, 20001L, 20003L);
    }

    @Test
    @DisplayName("cursor 이후 데이터만 반환하고 이전 페이지 마지막 리뷰를 중복하지 않아야 한다")
    void should_return_reviews_after_cursor_without_duplicate_last_review() {
        // Given: 첫 페이지 2건을 helpfulVote DESC로 조회한다.
        List<ReviewSummaryProjection> firstPage = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                2
        );
        ReviewSummaryProjection lastRow = firstPage.get(1);

        // When: 마지막 리뷰를 cursor로 다음 페이지를 조회한다.
        List<ReviewSummaryProjection> nextPage = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                lastRow.getId(),
                lastRow.getTimestamp(),
                lastRow.getHelpfulVote(),
                null,
                20
        );

        // Then: 이전 페이지 마지막 리뷰는 다음 페이지에 다시 나오면 안 된다.
        assertThat(nextPage).isNotEmpty();
        assertThat(nextPage).extracting(ReviewSummaryProjection::getId)
                .doesNotContain(lastRow.getId());
    }

    @Test
    @DisplayName("리뷰 id 목록으로 이미지들을 조회해 review별로 그룹핑 가능하게 반환해야 한다")
    void should_find_review_images_grouped_by_review_ids_when_review_ids_are_given() {
        // Given: 이미지가 2개 달린 리뷰 id 20001이 존재한다.

        // When: review id 목록으로 이미지를 조회한다.
        List<ReviewImageProjection> images =
                reviewImageRepository.findReviewImagesByReviewIds(List.of(20001L));

        // Then: review id 기준 그룹핑이 가능하도록 두 개의 image row가 반환되어야 한다.
        assertThat(images).hasSize(2);
        assertThat(images).allSatisfy(image ->
                assertThat(image.getReviewId()).isEqualTo(20001L)
        );
    }

    @Test
    @DisplayName("이미지가 없는 리뷰도 목록 조회에서 누락되지 않아야 한다")
    void should_keep_review_rows_when_reviews_do_not_have_images() {
        // Given: 이미지가 없는 리뷰 id 20002가 존재한다.

        // When: helpfulVote 정렬로 리뷰를 조회한다.
        List<ReviewSummaryProjection> rows = reviewRepository.findReviewSummaries(
                TARGET_PARENT_ASIN,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                20
        );

        // Then: 이미지가 없어도 리뷰 row 자체는 유지되어야 한다.
        assertThat(rows).extracting(ReviewSummaryProjection::getId)
                .contains(20002L);
    }

    private void insertRealFixtureRows() throws Exception {
        long reviewId = 10001L;
        long imageId = 20001L;

        for (String line : REAL_REVIEW_JSONL.strip().split("\\R")) {
            JsonNode root = objectMapper.readTree(line);

            jdbcTemplate.update(
                    """
                    insert into review (
                        id, user_id, product_id, asin, rating, title, text, timestamp, verified_purchase, helpful_vote
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    reviewId,
                    root.get("user_id").asText(),
                    root.get("parent_asin").asText(),
                    root.get("asin").asText(),
                    root.get("rating").floatValue(),
                    root.get("title").asText(),
                    root.get("text").asText(),
                    root.get("timestamp").asLong(),
                    root.get("verified_purchase").asBoolean(),
                    root.get("helpful_vote").asInt()
            );

            List<JsonNode> images = new ArrayList<>();
            root.get("images").forEach(images::add);
            for (JsonNode image : images) {
                jdbcTemplate.update(
                        """
                        insert into review_image (
                            id, review_id, small_image_url, medium_image_url, large_image_url, attachment_type
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                        imageId++,
                        reviewId,
                        image.get("small_image_url").asText(),
                        image.get("medium_image_url").asText(),
                        image.get("large_image_url").asText(),
                        image.get("attachment_type").asText()
                );
            }
            reviewId++;
        }
    }

    private void insertAdditionalRow(
            Long id,
            String parentAsin,
            String userId,
            Integer helpfulVote,
            Boolean verifiedPurchase,
            Float rating,
            String title,
            String text,
            String asin,
            Long timestamp
    ) {
        jdbcTemplate.update(
                """
                insert into review (
                    id, user_id, product_id, asin, rating, title, text, timestamp, verified_purchase, helpful_vote
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                parentAsin,
                asin,
                rating,
                title,
                text,
                timestamp,
                verifiedPurchase,
                helpfulVote
        );
    }

    private void insertReviewImage(
            Long id,
            Long reviewId,
            String smallImageUrl,
            String mediumImageUrl,
            String largeImageUrl,
            String attachmentType
    ) {
        jdbcTemplate.update(
                """
                insert into review_image (
                    id, review_id, small_image_url, medium_image_url, large_image_url, attachment_type
                ) values (?, ?, ?, ?, ?, ?)
                """,
                id,
                reviewId,
                smallImageUrl,
                mediumImageUrl,
                largeImageUrl,
                attachmentType
        );
    }
}
