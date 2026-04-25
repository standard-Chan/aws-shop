package jeong.awsshop.product.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import jeong.awsshop.product.service.dataimport.BulkInsertService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/data-import")
@AllArgsConstructor
public class DataImportController {

    private final BulkInsertService bulkInsertService;

    /**
     * JSONL 파일을 스트림으로 받아서 DB에 batch insert하는 API
     */
    @PostMapping("/upload")
    public String bulkupload(HttpServletRequest request, @RequestParam(defaultValue = "failed_rows") String filename) throws IOException {
        InputStream inputStream = request.getInputStream();

        bulkInsertService.bulkInsert(inputStream, filename);
        return "ok";
    }
}
