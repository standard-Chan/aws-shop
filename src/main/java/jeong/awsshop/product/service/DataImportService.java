package jeong.awsshop.product.service;

import jeong.awsshop.product.exception.dataimport.DataImportDuplicateParentAsinException;
import jeong.awsshop.product.exception.dataimport.DataImportPersistenceException;
import jeong.awsshop.product.exception.dataimport.DataImportParsingException;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.service.dataimport.DataImportJsonParser;
import jeong.awsshop.product.service.dataimport.DataImportProduct;
import jeong.awsshop.product.service.dataimport.DataImportProductAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대용량 데이터들을 DB에 IMPORT 하기 위한 로직
 */
@Service
public class DataImportService {

    private final DataImportJsonParser jsonParser;
    private final DataImportProductAssembler productAssembler;
    private final ProductRepository productRepository;

    public DataImportService(DataImportJsonParser jsonParser, DataImportProductAssembler productAssembler,
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
        } catch (DataImportParsingException e) {
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
