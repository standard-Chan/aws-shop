package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.product.repository.BulkInsertRepository;
import jeong.awsshop.product.service.dataimport.dto.ProductDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class BulkInsertService {

    private final ObjectMapper objectMapper;
    private final BulkInsertRepository bulkInsertRepository;

    public void bulkInsert(InputStream inputStream) {
        List<ProductDto> buffer = new ArrayList<>();
        int batchSize = 1500;

        String FAILED_ROWS_FILE_PATH = "./aws-dataset/failed_rows.jsonl";
        int totalProcessed = 0;

        try {
            Files.createDirectories(Paths.get(FAILED_ROWS_FILE_PATH).getParent());
        } catch (IOException e) {
            log.error("[Bulk Insert] Failed to create failed rows directory", e);
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
            Paths.get(FAILED_ROWS_FILE_PATH),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(inputStream, StandardCharsets.UTF_8)
             )
        ) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                ProductDto productDto = parseLine(writer, line);
                if (productDto == null) {
                    continue;
                }
                buffer.add(productDto);
                totalProcessed += 1;

                if (buffer.size() >= batchSize) {
                    List<ProductDto> failedBatch = bulkInsertRepository.bulkInsert(buffer, batchSize);

                    // 실패하는 경우 별도 파일에 JSONL 형식으로 저장
                    writeFailedRows(writer, failedBatch);

                    buffer.clear(); // 메모리 제거
                }
                if (totalProcessed > 0 && totalProcessed % 10000 == 0) {
                    log.info("[BulkInsert] {} rows completed", totalProcessed);
                }
            }

            if (!buffer.isEmpty()) {
                List<ProductDto> failedBatch = bulkInsertRepository.bulkInsert(buffer, batchSize);

                writeFailedRows(writer, failedBatch);

                buffer.clear();
            }

        } catch (IOException e) {
            log.error("[Bulk Insert] IO Exception during bulk insert", e);
        }
    }

    private ProductDto parseLine(BufferedWriter writer, String line) throws IOException {
        JsonNode jsonNode;

        try {
            jsonNode = objectMapper.readTree(line);
        } catch (Exception e) {
            log.error("[Product JSONL 파싱 실패]: 실패한 line을 실패 JSONL에 저장하고 다음 line을 처리합니다. json: {}", line, e);
            writeFailedRow(writer, line);
            return null;
        }

        try {
            return objectMapper.treeToValue(jsonNode, ProductDto.class);
        } catch (Exception e) {
            log.error("[Product DTO 변환 실패]: 실패한 line을 실패 JSONL에 저장하고 다음 line을 처리합니다. json: {}", line, e);
            writeFailedRow(writer, line);
            return null;
        }
    }

    private void writeFailedRow(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    /**
     * 실패한 행들을 JSONL 파일로 저장하는 메서드
     */
    private void writeFailedRows(BufferedWriter writer, List<ProductDto> failedRows) throws IOException {
        if (failedRows == null || failedRows.isEmpty()) {
            return;
        }

        for (ProductDto dto : failedRows) {
            writer.write(objectMapper.writeValueAsString(dto));
            writer.newLine();
        }
    }
}
