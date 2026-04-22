package jeong.awsshop.review.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.review.dto.ReviewBulkUploadResponse;
import jeong.awsshop.review.dto.ReviewDto;
import jeong.awsshop.review.repository.ReviewBulkInsertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewBulkUploadServiceTest {

    @Mock
    private ReviewBulkInsertRepository reviewBulkInsertRepository;

    private ReviewBulkUploadService reviewBulkUploadService;
    private static final String FAILED_JSONL_FILE_PATH = "./aws-dataset/reviews/failed-reviews.jsonl";

    private static final String VALID_REVIEW_JSONL = """
        {"rating":1.0,"title":"USELESS","text":"Absolutely useless nonsense and a complete waste of money.","images":[],"asin":"B07G584SHG","parent_asin":"B09WC47S3V","user_id":"AEMJ2EG5ODOCYUTI54NBXZHDJGSQ","timestamp":1602133857705,"helpful_vote":2,"verified_purchase":true}
        """;

    private static final String REVIEW_WITH_IMAGE_JSONL = """
        {"rating":5.0,"title":"Big Boy Hearts Bark Box","text":"There is no other subscription box for dogs like Bark Box.","images":[{"small_image_url":"https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL256_.jpg","medium_image_url":"https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL800_.jpg","large_image_url":"https://images-na.ssl-images-amazon.com/images/I/61602k-EjKL._SL1600_.jpg","attachment_type":"IMAGE"}],"asin":"B07R7WVRGL","parent_asin":"B08N5QKX1Y","user_id":"AEDTXOC3YW6O7P2UPM22VNNRF77A","timestamp":1563230263551,"helpful_vote":3,"verified_purchase":false}
        """;

    @BeforeEach
    void setUp() throws Exception {
        // Given: service가 사용할 repository와 ObjectMapper를 준비한다
        Files.deleteIfExists(Paths.get(FAILED_JSONL_FILE_PATH));
        reviewBulkUploadService = new ReviewBulkUploadService(
                new ObjectMapper(),
                reviewBulkInsertRepository
        );
    }

    @Test
    @DisplayName("유효한 review JSONL stream이면 파싱 후 repository에 저장 요청해야 한다")
    void should_parse_and_upload_valid_review_jsonl_when_valid_stream_is_given() {
        // Given: repository가 batch insert에 성공하도록 준비한다
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        InputStream inputStream = new ByteArrayInputStream(VALID_REVIEW_JSONL.getBytes(UTF_8));

        // When: 유효한 JSONL stream을 업로드한다
        ReviewBulkUploadResponse response =
                reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 실패 JSONL 파일 위치만 응답해야 한다
        assertThat(response.failedJsonlLocation()).isEqualTo("failed-reviews.jsonl");

        // Then: repository에 파싱된 DTO 1건이 전달되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches).hasSize(1);
        assertThat(capturedBatches.get(0)).hasSize(1);
        assertThat(capturedBatches.get(0).get(0).asin()).isEqualTo("B07G584SHG");
        assertThat(capturedBatches.get(0).get(0).parentAsin()).isEqualTo("B09WC47S3V");
    }

    @Test
    @DisplayName("image가 있으면 URL과 attachment_type을 repository DTO에 매핑해야 한다")
    void should_map_image_fields_including_attachment_type_when_review_has_images() {
        // Given: image가 있는 review JSONL과 성공하는 repository
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        InputStream inputStream = new ByteArrayInputStream(REVIEW_WITH_IMAGE_JSONL.getBytes(UTF_8));

        // When: image 포함 JSONL을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: image URL과 attachmentType이 repository DTO에 포함되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches.get(0).get(0).images()).hasSize(1);
        assertThat(capturedBatches.get(0).get(0).images().get(0).smallImageUrl())
                .contains("_SL256_");
        assertThat(capturedBatches.get(0).get(0).images().get(0).mediumImageUrl())
                .contains("_SL800_");
        assertThat(capturedBatches.get(0).get(0).images().get(0).largeImageUrl())
                .contains("_SL1600_");
        assertThat(capturedBatches.get(0).get(0).images().get(0).attachmentType())
                .isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("blank line이 있어도 정상 record를 저장해야 한다")
    void should_ignore_blank_lines_as_json_whitespace_when_stream_contains_blank_lines() {
        // Given: blank line과 정상 line이 섞인 stream
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        String jsonl = "\n" + VALID_REVIEW_JSONL + "\n   \n";
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: JSONL stream을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 정상 record만 repository에 전달되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches.get(0)).hasSize(1);
    }

    @Test
    @DisplayName("JSONL 파싱 에러가 발생하면 실패 line을 저장하고 다음 line을 계속 처리해야 한다")
    void should_write_failed_line_and_continue_when_jsonl_parse_error_occurs() throws Exception {
        // Given: 깨진 JSON line 사이에 정상 line이 있는 stream
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        String invalidLine = "{\"rating\":1.0,\"title\":\"broken\"";
        String jsonl = VALID_REVIEW_JSONL + invalidLine + "\n" + REVIEW_WITH_IMAGE_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: 파싱 실패 line이 포함된 JSONL stream을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 실패 line은 failed JSONL에 저장되고 정상 line 처리는 계속되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches.get(0)).hasSize(2);
        assertThat(capturedBatches.get(0)).extracting(ReviewDto::asin)
                .containsExactly("B07G584SHG", "B07R7WVRGL");

        String failedJsonl = Files.readString(Paths.get(FAILED_JSONL_FILE_PATH));
        assertThat(failedJsonl).contains(invalidLine);
    }

    @Test
    @DisplayName("잘못된 형식의 JSON 객체가 들어오면 실패 line을 저장하고 다음 line을 계속 처리해야 한다")
    void should_write_failed_line_and_continue_when_json_mapping_error_occurs() throws Exception {
        // Given: images field 형식이 배열이 아닌 실패 line이 있다
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        String invalidLine = """
            {"rating":1.0,"title":"Invalid images","text":"broken","images":{"small_image_url":"small"},"asin":"B000BROKEN","parent_asin":"PARENT","user_id":"USER","timestamp":1602133857705,"helpful_vote":0,"verified_purchase":true}
            """.strip();
        String jsonl = invalidLine + "\n" + VALID_REVIEW_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: mapping 실패 line이 포함된 JSONL stream을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 실패 line은 failed JSONL에 저장되고 정상 line은 repository로 전달되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches.get(0)).hasSize(1);
        assertThat(capturedBatches.get(0).get(0).asin()).isEqualTo("B07G584SHG");

        String failedJsonl = Files.readString(Paths.get(FAILED_JSONL_FILE_PATH));
        assertThat(failedJsonl).contains(invalidLine);
    }

    @Test
    @DisplayName("DTO 변환 에러가 발생하면 실패 line을 저장하고 다음 line을 계속 처리해야 한다")
    void should_write_failed_line_and_continue_when_dto_conversion_error_occurs() throws Exception {
        // Given: JSON 파싱은 가능하지만 ReviewDto 타입으로 변환할 수 없는 line이 있다
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        String invalidLine = """
            {"rating":{"value":1.0},"title":"Invalid rating","text":"broken","images":[],"asin":"B000BROKEN","parent_asin":"PARENT","user_id":"USER","timestamp":1602133857705,"helpful_vote":0,"verified_purchase":true}
            """.strip();
        String jsonl = VALID_REVIEW_JSONL + invalidLine + "\n" + REVIEW_WITH_IMAGE_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: DTO 변환 실패 line이 포함된 JSONL stream을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 실패 line은 failed JSONL에 저장되고 다음 정상 line은 계속 처리되어야 한다
        verify(reviewBulkInsertRepository).bulkInsert(anyList());
        assertThat(capturedBatches.get(0)).hasSize(2);
        assertThat(capturedBatches.get(0)).extracting(ReviewDto::asin)
                .containsExactly("B07G584SHG", "B07R7WVRGL");

        String failedJsonl = Files.readString(Paths.get(FAILED_JSONL_FILE_PATH));
        assertThat(failedJsonl).contains(invalidLine);
    }

    @Test
    @DisplayName("batch size에 도달하면 batch 단위로 repository를 호출해야 한다")
    void should_flush_batch_when_record_count_reaches_batch_size() {
        // Given: 정상 line 3건과 batch size 2
        List<List<ReviewDto>> capturedBatches = captureSuccessfulBatches();
        String jsonl = VALID_REVIEW_JSONL + VALID_REVIEW_JSONL + VALID_REVIEW_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: batch size 2로 업로드한다
        reviewBulkUploadService.upload(inputStream, 2, "failed-reviews");

        // Then: 2건 batch와 1건 batch로 repository가 호출되어야 한다
        verify(reviewBulkInsertRepository, times(2)).bulkInsert(anyList());
        assertThat(capturedBatches).extracting(List::size)
                .containsExactly(2, 1);
    }

    @Test
    @DisplayName("repository insert 실패 시 실패 batch DTO 전체를 실패 JSONL에 저장해야 한다")
    void should_write_failed_batch_snapshot_when_repository_insert_fails() throws Exception {
        // Given: repository가 전달받은 batch 전체를 실패 batch로 반환한다
        when(reviewBulkInsertRepository.bulkInsert(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        String jsonl = VALID_REVIEW_JSONL + REVIEW_WITH_IMAGE_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: repository insert가 실패하는 stream을 업로드한다
        reviewBulkUploadService.upload(inputStream, 100, "failed-reviews");

        // Then: 실패 batch의 DTO 전체가 실패 JSONL에 저장되어야 한다
        String failedJsonl = Files.readString(Paths.get(FAILED_JSONL_FILE_PATH));
        assertThat(failedJsonl).contains("\"asin\":\"B07G584SHG\"");
        assertThat(failedJsonl).contains("\"asin\":\"B07R7WVRGL\"");
    }

    private List<List<ReviewDto>> captureSuccessfulBatches() {
        List<List<ReviewDto>> capturedBatches = new ArrayList<>();
        when(reviewBulkInsertRepository.bulkInsert(anyList()))
                .thenAnswer(invocation -> {
                    List<ReviewDto> batch = invocation.getArgument(0);
                    capturedBatches.add(new ArrayList<>(batch));
                    return List.of();
                });
        return capturedBatches;
    }
}
