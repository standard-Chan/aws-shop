package jeong.awsshop.review.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import jeong.awsshop.review.dto.ReviewBulkUploadResponse;
import jeong.awsshop.review.service.ReviewBulkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewBulkUploadController {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 1000;

    private final ReviewBulkUploadService reviewBulkUploadService;

    @PostMapping("/api/reviews/bulk-upload/jsonl")
    public ResponseEntity<ReviewBulkUploadResponse> upload(
            HttpServletRequest request,
            @RequestParam(name = "batch-size", required = false, defaultValue = "100") int batchSize,
            @RequestParam(name = "filename") String filename
    ) throws IOException {
        // 요청 파라미터를 service 호출 전에 검증한다.
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ReviewBulkUploadResponse response = reviewBulkUploadService.upload(
                request.getInputStream(),
                batchSize == 0 ? DEFAULT_BATCH_SIZE : batchSize,
                filename
        );
        return ResponseEntity.ok(response);
    }
}
