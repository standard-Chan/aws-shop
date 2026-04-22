package jeong.awsshop.review.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import jeong.awsshop.review.dto.ReviewBulkUploadResponse;
import jeong.awsshop.review.service.ReviewBulkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class ReviewBulkUploadController {

    private final ReviewBulkUploadService reviewBulkUploadService;

    @PostMapping("/api/reviews/bulk-upload")
    public ResponseEntity<ReviewBulkUploadResponse> upload(
            HttpServletRequest request,
            @RequestParam(name = "batch-size", required = false, defaultValue = "100")
            @Min(1) @Max(1000) int batchSize,
            @RequestParam(name = "filename") @NotBlank String filename
    ) throws IOException {
        ReviewBulkUploadResponse response = reviewBulkUploadService.upload(
                request.getInputStream(),
                batchSize,
                filename
        );
        return ResponseEntity.ok(response);
    }
}
