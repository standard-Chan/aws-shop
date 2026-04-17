package jeong.awsshop.product.service.dataimport;

import jeong.awsshop.product.exception.dataimport.DataImportDuplicateParentAsinException;
import jeong.awsshop.product.exception.dataimport.DataImportPersistenceException;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.service.dataimport.dto.DataImportProduct;
import jeong.awsshop.common.exception.JsonParsingException;
import jeong.awsshop.common.json.JsonTreeParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대용량 데이터들을 DB에 IMPORT 하기 위한 로직
 */
@Service
public class DataImportService {

    private final JsonTreeParser jsonParser;
    private final DataImportProductAssembler productAssembler;
    private final ProductRepository productRepository;

    public DataImportService(JsonTreeParser jsonParser, DataImportProductAssembler productAssembler,
                             ProductRepository productRepository) {
        this.jsonParser = jsonParser;
        this.productAssembler = productAssembler;
        this.productRepository = productRepository;
    }

    @Transactional
    public void insert(String jsonLine) {
        DataImportProduct dataImportProduct;
        try {
            dataImportProduct = productAssembler.assemble(jsonParser.parse(jsonLine));
        } catch (JsonParsingException e) {
            return;
        }

        if (dataImportProduct == null) {
            return;
        }

        if (productRepository.existsByParentAsin(dataImportProduct.parentAsin())) {
            throw new DataImportDuplicateParentAsinException(dataImportProduct.parentAsin());
        }

        try {
            productRepository.saveAndFlush(dataImportProduct.product());
        } catch (RuntimeException e) {
            throw new DataImportPersistenceException(e.getMessage(), e);
        }
    }
}
