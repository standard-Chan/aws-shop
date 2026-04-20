package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
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

        String FAILED_ROWS_FILE_PATH = "./aws-dataset/failed_rows.jsonl";

        try (BufferedWriter writer = Files.newBufferedWriter(
            Paths.get(FAILED_ROWS_FILE_PATH),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {

            JsonParser parser = objectMapper.getFactory().createParser(inputStream);

            while (parser.nextToken() != null) {
                ProductDto productDto = objectMapper.readValue(parser, ProductDto.class);
                buffer.add(productDto);

                if (buffer.size() >= 100) {
                    List<ProductDto> failedBatch = bulkInsertRepository.bulkInsert(buffer);

                    // 실패하는 경우 별도 파일에 JSONL 형식으로 저장
                    writeFailedRows(writer, failedBatch);

                    buffer.clear(); // 메모리 제거
                }
            }

            if (!buffer.isEmpty()) {
                List<ProductDto> failedBatch = bulkInsertRepository.bulkInsert(buffer);

                writeFailedRows(writer, failedBatch);

                buffer.clear();
            }

        } catch (IOException e) {
            log.error("[Bulk Insert] IO Exception during bulk insert", e);
        }
    }

    /**
     * 실패한 행들을 JSONL 파일로 저장하는 메서드
     */
    private void writeFailedRows(BufferedWriter writer, List<ProductDto> failedRows) throws IOException {
        if (failedRows == null || failedRows.isEmpty()) return;

        for (ProductDto dto : failedRows) {
            writer.write(objectMapper.writeValueAsString(dto));
            writer.newLine();
        }
    }
}
