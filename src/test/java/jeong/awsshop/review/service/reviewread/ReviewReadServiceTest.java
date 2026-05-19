package jeong.awsshop.review.service.reviewread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jeong.awsshop.review.exception.reviewread.InvalidReviewCursorException;
import jeong.awsshop.review.repository.ReviewImageRepository;
import jeong.awsshop.review.repository.ReviewRepository;
import jeong.awsshop.review.repository.projection.ReviewImageProjection;
import jeong.awsshop.review.repository.projection.ReviewSummaryProjection;
import jeong.awsshop.review.service.reviewread.dto.ReviewCursorResponse;
import jeong.awsshop.review.service.reviewread.dto.ReviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewReadServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @InjectMocks
    private ReviewReadService reviewReadService;

    @Test
    @DisplayName("리뷰 조회 시 repository limit은 size + 1이어야 한다")
    void should_return_first_page_sorted_by_helpful_vote_desc_when_cursor_is_absent() {
        // Given: 기본 helpfulVote DESC 정렬의 첫 페이지 조회 조건이다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                3
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20001L, 5.0F, 11, 1653846936825L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20001L)))
                .thenReturn(List.of());

        // When: cursor 없이 size 2로 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                2,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );

        // Then: 기본 정렬 순서와 size + 1 조회 계약이 유지되어야 한다.
        assertThat(response.reviews()).extracting(ReviewResponse::id)
                .containsExactly(20002L, 20001L);
        verify(reviewRepository).findReviewSummaries(
                eq("B096MTTDJL"),
                eq("helpfulVote"),
                eq("desc"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(3)
        );
    }

    @Test
    @DisplayName("helpfulVote 정렬이면 helpfulVote가 null인 리뷰를 응답에서 제외해야 한다")
    void should_exclude_reviews_with_null_helpful_vote_when_sort_is_helpful_vote() {
        // Given: repository가 helpfulVote null row를 함께 반환한다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                4
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20005L, 4.0F, null, 1653846936500L, "B096MTTDJL"),
                summary(20001L, 5.0F, 11, 1653846936825L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20001L)))
                .thenReturn(List.of());

        // When: helpfulVote 정렬로 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                3,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );

        // Then: helpfulVote가 null인 리뷰는 응답에 포함되면 안 된다.
        assertThat(response.reviews()).extracting(ReviewResponse::id)
                .containsExactly(20002L, 20001L);
    }

    @Test
    @DisplayName("rating ASC 정렬이면 rating 기준으로 조회해야 한다")
    void should_return_reviews_sorted_by_rating_asc_when_sort_is_rating_and_direction_is_asc() {
        // Given: rating ASC 정렬 결과와 image 조회 결과를 준비한다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "rating",
                "asc",
                null,
                null,
                null,
                null,
                3
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20004L, 4.0F, 11, 1653846936800L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20004L)))
                .thenReturn(List.of());

        // When: rating ASC로 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                2,
                "rating",
                "asc",
                null,
                null,
                null,
                null
        );

        // Then: rating 오름차순 응답이 반환되어야 한다.
        assertThat(response.reviews()).extracting(ReviewResponse::rating)
                .containsExactly(2.0F, 4.0F);
    }

    @Test
    @DisplayName("rating 정렬이면 rating이 null인 리뷰를 응답에서 제외해야 한다")
    void should_exclude_reviews_with_null_rating_when_sort_is_rating() {
        // Given: repository가 rating null row를 함께 반환한다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "rating",
                "asc",
                null,
                null,
                null,
                null,
                4
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20006L, null, 3, 1653846936400L, "B096MTTDJL"),
                summary(20004L, 4.0F, 11, 1653846936800L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20004L)))
                .thenReturn(List.of());

        // When: rating 정렬로 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                3,
                "rating",
                "asc",
                null,
                null,
                null,
                null
        );

        // Then: rating null row는 응답에 포함되면 안 된다.
        assertThat(response.reviews()).extracting(ReviewResponse::id)
                .containsExactly(20002L, 20004L);
    }

    @Test
    @DisplayName("조회 row가 size보다 많으면 hasNext는 true이고 응답은 size개만 포함해야 한다")
    void should_return_has_next_true_and_trim_results_when_repository_returns_size_plus_one() {
        // Given: repository가 size + 1개를 반환한다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                3
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20001L, 5.0F, 11, 1653846936825L, "B096MTTDJL"),
                summary(20003L, 5.0F, 11, 1653846936825L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20001L)))
                .thenReturn(List.of(
                        image(20001L,
                                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL256_.jpg",
                                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL800_.jpg",
                                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL1600_.jpg",
                                "IMAGE")
                ));

        // When: size 2로 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                2,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );

        // Then: 응답은 size개만 담고 다음 페이지가 있다고 표시해야 한다.
        assertThat(response.reviews()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("응답 리뷰가 있으면 마지막 리뷰 기준으로 nextCursor를 계산해야 한다")
    void should_return_next_cursor_from_last_review_when_has_more_pages() {
        // Given: 두 개의 응답 대상 리뷰를 준비한다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                3
        )).thenReturn(List.of(
                summary(20002L, 2.0F, 12, 1653846937000L, "B096MTTDJL"),
                summary(20001L, 5.0F, 11, 1653846936825L, "B096MTTDJL")
        ));
        when(reviewImageRepository.findReviewImagesByReviewIds(List.of(20002L, 20001L)))
                .thenReturn(List.of());

        // When: 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                2,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );

        // Then: 마지막 리뷰의 cursor 정보가 응답에 반영되어야 한다.
        assertThat(response.nextCursor().id()).isEqualTo(20001L);
        assertThat(response.nextCursor().timestamp()).isEqualTo(1653846936825L);
        assertThat(response.nextCursor().helpfulVote()).isEqualTo(11);
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 응답과 hasNext false를 반환해야 한다")
    void should_return_empty_response_when_reviews_do_not_exist() {
        // Given: repository 조회 결과가 비어 있다.
        when(reviewRepository.findReviewSummaries(
                "B096MTTDJL",
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null,
                3
        )).thenReturn(List.of());

        // When: 리뷰를 조회한다.
        ReviewCursorResponse response = reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                2,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );

        // Then: 빈 목록과 null cursor가 반환되어야 한다.
        assertThat(response.reviews()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("cursor 조합이 불완전하면 InvalidReviewCursorException을 던져야 한다")
    void should_throw_invalid_review_cursor_exception_when_cursor_combination_is_incomplete() {
        // Given: 첫 페이지가 아닌데 cursorTimestamp가 없다.

        // When & Then: 필수 cursor 조합이 불완전하면 예외가 발생해야 한다.
        assertThatThrownBy(() -> reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                20,
                "helpfulVote",
                "desc",
                20001L,
                null,
                11,
                null
        )).isInstanceOf(InvalidReviewCursorException.class);
    }

    @Test
    @DisplayName("helpfulVote 정렬에서 cursorHelpfulVote가 없으면 InvalidReviewCursorException을 던져야 한다")
    void should_throw_invalid_review_cursor_exception_when_helpful_vote_cursor_is_missing() {
        // Given: helpfulVote 정렬인데 cursorHelpfulVote가 빠져 있다.

        // When & Then: 정렬 기준 cursor가 없으면 예외가 발생해야 한다.
        assertThatThrownBy(() -> reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                20,
                "helpfulVote",
                "desc",
                20001L,
                1653846936825L,
                null,
                null
        )).isInstanceOf(InvalidReviewCursorException.class);
    }

    @Test
    @DisplayName("rating 정렬에서 cursorRating이 없으면 InvalidReviewCursorException을 던져야 한다")
    void should_throw_invalid_review_cursor_exception_when_rating_cursor_is_missing() {
        // Given: rating 정렬인데 cursorRating이 빠져 있다.

        // When & Then: rating cursor가 없으면 예외가 발생해야 한다.
        assertThatThrownBy(() -> reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                20,
                "rating",
                "asc",
                20001L,
                1653846936825L,
                null,
                null
        )).isInstanceOf(InvalidReviewCursorException.class);
    }

    private ReviewSummaryProjection summary(
            Long id,
            Float rating,
            Integer helpfulVote,
            Long timestamp,
            String parentAsin
    ) {
        return new ReviewSummaryProjection() {
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
                return "fixture-title-" + id;
            }

            @Override
            public String getText() {
                return "fixture-text-" + id;
            }

            @Override
            public Long getTimestamp() {
                return timestamp;
            }

            @Override
            public String getUserId() {
                return "fixture-user-" + id;
            }

            @Override
            public Boolean getVerifiedPurchase() {
                return true;
            }

            @Override
            public Integer getHelpfulVote() {
                return helpfulVote;
            }

            @Override
            public String getAsin() {
                return "fixture-asin-" + id;
            }

            @Override
            public String getParentAsin() {
                return parentAsin;
            }
        };
    }

    private ReviewImageProjection image(
            Long reviewId,
            String smallImageUrl,
            String mediumImageUrl,
            String largeImageUrl,
            String attachmentType
    ) {
        return new ReviewImageProjection() {
            @Override
            public Long getReviewId() {
                return reviewId;
            }

            @Override
            public String getSmallImageUrl() {
                return smallImageUrl;
            }

            @Override
            public String getMediumImageUrl() {
                return mediumImageUrl;
            }

            @Override
            public String getLargeImageUrl() {
                return largeImageUrl;
            }

            @Override
            public String getAttachmentType() {
                return attachmentType;
            }
        };
    }
}
