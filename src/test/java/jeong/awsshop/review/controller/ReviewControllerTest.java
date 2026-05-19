package jeong.awsshop.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jeong.awsshop.review.service.reviewread.ReviewReadService;
import jeong.awsshop.review.service.reviewread.dto.ReviewCursor;
import jeong.awsshop.review.service.reviewread.dto.ReviewCursorResponse;
import jeong.awsshop.review.service.reviewread.dto.ReviewImageResponse;
import jeong.awsshop.review.service.reviewread.dto.ReviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewReadService reviewReadService;

    @Test
    @DisplayName("리뷰 조회에서 선택 query parameter를 생략하면 기본값으로 service를 호출해야 한다")
    void should_call_service_with_default_values_when_optional_query_parameters_are_omitted() throws Exception {
        // Given: service가 빈 cursor 응답을 반환하도록 준비한다.
        when(reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                20,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        )).thenReturn(new ReviewCursorResponse(List.of(), null, false));

        // When: size, sort, direction 없이 리뷰 조회 API를 호출한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL"))
                .andExpect(status().isOk());

        // Then: controller는 기본값을 적용해 service에 위임해야 한다.
        verify(reviewReadService).getReviewsByProductId(
                "B096MTTDJL",
                20,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("다음 페이지 요청이면 cursor와 정렬 query parameter를 service에 전달해야 한다")
    void should_call_service_with_cursor_parameters_when_next_page_is_requested() throws Exception {
        // Given: rating ASC 기준 다음 페이지 조회 응답을 준비한다.
        ReviewCursorResponse response = new ReviewCursorResponse(
                List.of(),
                new ReviewCursor(20003L, 1653846936825L, null, 5.0F),
                true
        );
        when(reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                10,
                "rating",
                "asc",
                20001L,
                1653846936825L,
                null,
                5.0F
        )).thenReturn(response);

        // When: rating cursor query parameter를 포함해 API를 호출한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL")
                        .param("size", "10")
                        .param("sort", "rating")
                        .param("direction", "asc")
                        .param("cursorId", "20001")
                        .param("cursorTimestamp", "1653846936825")
                        .param("cursorRating", "5.0"))
                .andExpect(status().isOk());

        // Then: controller는 query parameter를 그대로 service에 전달해야 한다.
        verify(reviewReadService).getReviewsByProductId(
                "B096MTTDJL",
                10,
                "rating",
                "asc",
                20001L,
                1653846936825L,
                null,
                5.0F
        );
    }

    @Test
    @DisplayName("유효한 요청이면 review cursor 응답을 HTTP 200 JSON으로 반환해야 한다")
    void should_return_review_cursor_response_when_request_is_valid() throws Exception {
        // Given: 실제 fixture 기반 review와 image 응답을 준비한다.
        ReviewImageResponse image1 = new ReviewImageResponse(
                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL256_.jpg",
                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL800_.jpg",
                "https://m.media-amazon.com/images/I/71cGJgj94oL._SL1600_.jpg",
                "IMAGE"
        );
        ReviewImageResponse image2 = new ReviewImageResponse(
                "https://m.media-amazon.com/images/I/81HFhuFUScL._SL256_.jpg",
                "https://m.media-amazon.com/images/I/81HFhuFUScL._SL800_.jpg",
                "https://m.media-amazon.com/images/I/81HFhuFUScL._SL1600_.jpg",
                "IMAGE"
        );
        ReviewResponse review = new ReviewResponse(
                20001L,
                5.0F,
                "I absolutely love the colors & product. However , the price is too high",
                "I am always on the hunt for new lip gloss and lipstick . I decided to try this one on an Amazon daily deal.",
                1653846936825L,
                "AEUK73LJSRJOTGMKYNAC3CYHOX2Q",
                true,
                11,
                "B096MS17VY",
                "B096MTTDJL",
                List.of(image1, image2)
        );
        ReviewCursorResponse response = new ReviewCursorResponse(
                List.of(review),
                new ReviewCursor(20001L, 1653846936825L, 11, null),
                true
        );
        when(reviewReadService.getReviewsByProductId(
                "B096MTTDJL",
                1,
                "helpfulVote",
                "desc",
                null,
                null,
                null,
                null
        )).thenReturn(response);

        // When & Then: controller는 review 응답을 JSON으로 반환해야 한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].id").value(20001L))
                .andExpect(jsonPath("$.reviews[0].rating").value(5.0))
                .andExpect(jsonPath("$.reviews[0].parentAsin").value("B096MTTDJL"))
                .andExpect(jsonPath("$.reviews[0].images[0].attachmentType").value("IMAGE"))
                .andExpect(jsonPath("$.nextCursor.id").value(20001L))
                .andExpect(jsonPath("$.nextCursor.helpfulVote").value(11))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("size가 1 미만이면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_size_is_less_than_one() throws Exception {
        // Given: 허용 범위를 벗어난 size 값이다.

        // When & Then: size 0 요청은 거절되어야 한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        // Then: validation 실패 시 service는 호출되면 안 된다.
        verify(reviewReadService, never()).getReviewsByProductId(
                any(),
                any(Integer.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("size가 100 초과이면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_size_is_greater_than_hundred() throws Exception {
        // Given: 최대 size를 초과한 요청이다.

        // When & Then: size 101 요청은 거절되어야 한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cursor 조합이 불완전하면 400 Bad Request를 반환해야 한다")
    void should_return_bad_request_when_cursor_combination_is_invalid() throws Exception {
        // Given: 다음 페이지 요청에 필요한 cursor 조합이 일부만 주어진다.

        // When & Then: cursorId만 있는 요청은 거절되어야 한다.
        mockMvc.perform(get("/api/reviews/products/{parentAsin}", "B096MTTDJL")
                        .param("cursorId", "20001"))
                .andExpect(status().isBadRequest());

        // Then: 잘못된 cursor 요청은 service에 도달하면 안 된다.
        verify(reviewReadService, never()).getReviewsByProductId(
                any(),
                any(Integer.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}
