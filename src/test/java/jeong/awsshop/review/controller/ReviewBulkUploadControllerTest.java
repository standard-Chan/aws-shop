package jeong.awsshop.review.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import jeong.awsshop.review.dto.ReviewBulkUploadResponse;
import jeong.awsshop.review.service.ReviewBulkUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewBulkUploadController.class)
class ReviewBulkUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewBulkUploadService reviewBulkUploadService;

    private static final String VALID_REVIEW_JSONL = """
        {"rating":1.0,"title":"USELESS","text":"Absolutely useless nonsense and a complete waste of money.","images":[],"asin":"B07G584SHG","parent_asin":"B09WC47S3V","user_id":"AEMJ2EG5ODOCYUTI54NBXZHDJGSQ","timestamp":1602133857705,"helpful_vote":2,"verified_purchase":true}
        """;

    @Test
    @DisplayName("유효한 요청이면 request body stream과 파라미터를 service로 전달해야 한다")
    void should_call_service_with_request_body_stream_when_valid_request_is_given() throws Exception {
        // Given: service가 실패 JSONL 파일 위치를 반환하도록 준비한다
        ReviewBulkUploadResponse response = new ReviewBulkUploadResponse("failed-reviews.jsonl");
        when(reviewBulkUploadService.upload(any(InputStream.class), eq(100), eq("failed-reviews")))
                .thenReturn(response);

        // When: JSONL body stream으로 bulk upload API를 호출한다
        mockMvc.perform(post("/api/reviews/bulk-upload/jsonl")
                        .param("batch-size", "100")
                        .param("filename", "failed-reviews")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(VALID_REVIEW_JSONL.getBytes(UTF_8)))
                .andExpect(status().isOk());

        // Then: controller는 body stream과 query parameter를 service에 그대로 전달해야 한다
        verify(reviewBulkUploadService)
                .upload(any(InputStream.class), eq(100), eq("failed-reviews"));
    }

    @Test
    @DisplayName("batch-size가 허용 범위를 벗어나면 400을 반환하고 service를 호출하지 않아야 한다")
    void should_reject_request_when_batch_size_is_out_of_range() throws Exception {
        // Given: 허용 범위를 벗어난 batch-size

        // When & Then: batch-size가 0이면 요청을 거절해야 한다
        mockMvc.perform(post("/api/reviews/bulk-upload/jsonl")
                        .param("batch-size", "0")
                        .param("filename", "failed-reviews")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(VALID_REVIEW_JSONL.getBytes(UTF_8)))
                .andExpect(status().isBadRequest());

        // When & Then: batch-size가 1000을 초과하면 요청을 거절해야 한다
        mockMvc.perform(post("/api/reviews/bulk-upload/jsonl")
                        .param("batch-size", "1001")
                        .param("filename", "failed-reviews")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(VALID_REVIEW_JSONL.getBytes(UTF_8)))
                .andExpect(status().isBadRequest());

        // Then: 유효하지 않은 요청은 service로 전달하지 않아야 한다
        verify(reviewBulkUploadService, never())
                .upload(any(InputStream.class), any(Integer.class), any(String.class));
    }

    @Test
    @DisplayName("filename이 blank이면 400을 반환하고 service를 호출하지 않아야 한다")
    void should_reject_request_when_filename_is_blank() throws Exception {
        // Given: 실패 JSONL 파일명으로 사용할 수 없는 blank filename

        // When & Then: filename이 blank이면 요청을 거절해야 한다
        mockMvc.perform(post("/api/reviews/bulk-upload/jsonl")
                        .param("batch-size", "100")
                        .param("filename", " ")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(VALID_REVIEW_JSONL.getBytes(UTF_8)))
                .andExpect(status().isBadRequest());

        // Then: 실패 파일명이 없으면 service를 호출하지 않아야 한다
        verify(reviewBulkUploadService, never())
                .upload(any(InputStream.class), any(Integer.class), any(String.class));
    }
}
