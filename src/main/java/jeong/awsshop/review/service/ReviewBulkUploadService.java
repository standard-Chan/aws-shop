package jeong.awsshop.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import jeong.awsshop.review.dto.ReviewBulkUploadResponse;
import jeong.awsshop.review.dto.ReviewDto;
import jeong.awsshop.review.repository.ReviewBulkInsertRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ReviewBulkUploadService {

    private final ObjectMapper objectMapper;
    private final ReviewBulkInsertRepository reviewBulkInsertRepository;

    public ReviewBulkUploadResponse upload(InputStream inputStream, int batchSize, String filename) {
        List<ReviewDto> buffer = new ArrayList<>();
        Path failedRowsPath = Paths.get("./aws-dataset/reviews", filename + ".jsonl");

        try {
            Files.createDirectories(failedRowsPath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(
                    failedRowsPath,
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

                    ReviewDto reviewDto = parseLine(writer, line);
                    if (reviewDto == null) {
                        continue;
                    }

                    buffer.add(reviewDto);

                    if (buffer.size() >= batchSize) {
                        List<ReviewDto> failedBatch = reviewBulkInsertRepository.bulkInsert(buffer);
                        writeFailedRows(writer, failedBatch);
                        buffer.clear();
                    }
                }

                if (!buffer.isEmpty()) {
                    List<ReviewDto> failedBatch = reviewBulkInsertRepository.bulkInsert(buffer);
                    writeFailedRows(writer, failedBatch);
                    buffer.clear();
                }
            }
        } catch (IOException e) {
            log.error("[Review Bulk Upload 실패]: stream 또는 실패 JSONL 파일 처리 중 오류가 발생했습니다.", e);
        }

        return new ReviewBulkUploadResponse(failedRowsPath.getFileName().toString());
    }

    private ReviewDto parseLine(BufferedWriter writer, String line) throws IOException {
        JsonNode jsonNode;

        try {
            jsonNode = objectMapper.readTree(line);
        } catch (Exception e) {
            log.error("[Review JSONL 파싱 실패]: 실패한 line을 실패 JSONL에 저장하고 다음 line을 처리합니다. line: {}", line, e);
            writeFailedRow(writer, line);
            return null;
        }

        try {
            return objectMapper.treeToValue(jsonNode, ReviewDto.class);
        } catch (Exception e) {
            log.error("[Review DTO 변환 실패]: 실패한 line을 실패 JSONL에 저장하고 다음 line을 처리합니다. line: {}", line, e);
            writeFailedRow(writer, line);
            return null;
        }
    }

    private void writeFailedRow(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private void writeFailedRows(BufferedWriter writer, List<ReviewDto> failedRows) throws IOException {
        if (failedRows == null || failedRows.isEmpty()) {
            return;
        }

        for (ReviewDto dto : failedRows) {
            writer.write(objectMapper.writeValueAsString(dto));
            writer.newLine();
        }
    }
}
