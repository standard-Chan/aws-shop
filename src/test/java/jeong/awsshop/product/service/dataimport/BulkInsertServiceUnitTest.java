package jeong.awsshop.product.service.dataimport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.product.repository.BulkInsertRepository;
import jeong.awsshop.product.service.dataimport.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkInsertServiceUnitTest {

    private static final String FAILED_ROWS_FILE_PATH = "./aws-dataset/failed_rows.jsonl";

    private static final String VALID_PRODUCT_JSONL = """
        {"main_category":"Gift Cards","title":"Amazon.com Gift Card","average_rating":4.8,"rating_number":1006,"features":[],"description":[],"price":null,"images":[],"videos":[],"store":"Amazon","categories":[],"details":{},"parent_asin":"B06ZXTKYHN","bought_together":null}
        """;

    private static final String SECOND_VALID_PRODUCT_JSONL = """
        {"main_category":"Handmade","title":"Daisy Keychain","average_rating":4.5,"rating_number":12,"features":[],"description":[],"price":null,"images":[],"videos":[],"store":"Generic","categories":[],"details":{},"parent_asin":"B07NTK7T5P","bought_together":null}
        """;

    @Mock
    private BulkInsertRepository bulkInsertRepository;

    private BulkInsertService bulkInsertService;

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(Paths.get(FAILED_ROWS_FILE_PATH));
        bulkInsertService = new BulkInsertService(new ObjectMapper(), bulkInsertRepository);
    }

    @Test
    @DisplayName("JSONL 파싱 에러가 발생하면 실패 line을 저장하고 다음 line을 계속 처리해야 한다")
    void should_write_failed_line_and_continue_when_jsonl_parse_error_occurs() throws Exception {
        // Given: 깨진 JSON line 사이에 정상 product line이 있다
        List<List<ProductDto>> capturedBatches = captureSuccessfulBatches();
        String invalidLine = "{\"main_category\":\"Gift Cards\",\"title\":\"broken\"";
        String jsonl = VALID_PRODUCT_JSONL + invalidLine + "\n" + SECOND_VALID_PRODUCT_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: Product bulk upload를 실행한다
        bulkInsertService.bulkInsert(inputStream);

        // Then: 실패 line은 failed file에 저장되고 정상 line 처리는 계속되어야 한다
        verify(bulkInsertRepository).bulkInsert(anyList(), anyInt());
        assertThat(capturedBatches.get(0)).hasSize(2);
        assertThat(capturedBatches.get(0)).extracting(ProductDto::parentAsin)
                .containsExactly("B06ZXTKYHN", "B07NTK7T5P");

        String failedJsonl = Files.readString(Paths.get(FAILED_ROWS_FILE_PATH));
        assertThat(failedJsonl).contains(invalidLine);
    }

    @Test
    @DisplayName("DTO 변환 에러가 발생하면 실패 line을 저장하고 다음 line을 계속 처리해야 한다")
    void should_write_failed_line_and_continue_when_dto_conversion_error_occurs() throws Exception {
        // Given: JSON 파싱은 가능하지만 ProductDto 타입으로 변환할 수 없는 line이 있다
        List<List<ProductDto>> capturedBatches = captureSuccessfulBatches();
        String invalidLine = """
            {"main_category":"Gift Cards","title":"Invalid rating","average_rating":{"value":4.8},"rating_number":1006,"features":[],"description":[],"price":null,"images":[],"videos":[],"store":"Amazon","categories":[],"details":{},"parent_asin":"B000BROKEN","bought_together":null}
            """.strip();
        String jsonl = VALID_PRODUCT_JSONL + invalidLine + "\n" + SECOND_VALID_PRODUCT_JSONL;
        InputStream inputStream = new ByteArrayInputStream(jsonl.getBytes(UTF_8));

        // When: Product bulk upload를 실행한다
        bulkInsertService.bulkInsert(inputStream);

        // Then: 실패 line은 failed file에 저장되고 정상 line 처리는 계속되어야 한다
        verify(bulkInsertRepository).bulkInsert(anyList(), anyInt());
        assertThat(capturedBatches.get(0)).hasSize(2);
        assertThat(capturedBatches.get(0)).extracting(ProductDto::parentAsin)
                .containsExactly("B06ZXTKYHN", "B07NTK7T5P");

        String failedJsonl = Files.readString(Paths.get(FAILED_ROWS_FILE_PATH));
        assertThat(failedJsonl).contains(invalidLine);
    }

    private List<List<ProductDto>> captureSuccessfulBatches() {
        List<List<ProductDto>> capturedBatches = new ArrayList<>();
        when(bulkInsertRepository.bulkInsert(anyList(), anyInt()))
                .thenAnswer(invocation -> {
                    List<ProductDto> batch = invocation.getArgument(0);
                    capturedBatches.add(new ArrayList<>(batch));
                    return List.of();
                });
        return capturedBatches;
    }
}
