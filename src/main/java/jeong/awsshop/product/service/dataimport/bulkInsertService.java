package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
public class bulkInsertService {

    private final ObjectMapper objectMapper;
    private final BulkInsertRepository bulkInsertRepository;

    public void bulkInsert(InputStream inputStream) {
        List<ProductDto> buffer = new ArrayList<>();

        try {
            JsonParser parser = objectMapper.getFactory().createParser(inputStream);

            while (parser.nextToken() != null) {
                ProductDto productDto = objectMapper.readValue(parser, ProductDto.class);
                log.debug("[Bulk Insert] Product({}) 파싱 완료 ", productDto.parentAsin());
                buffer.add(productDto);

                if (buffer.size() >= 100) {
                    bulkInsertRepository.bulkInsert(buffer);
                    buffer.clear();
                }
            }

            if (!buffer.isEmpty()) {
                bulkInsertRepository.bulkInsert(buffer);
            }
        } catch (IOException e) {
            log.error("[Bulk Insert] IO Exception during bulk insert", e);
        }
    }
}
