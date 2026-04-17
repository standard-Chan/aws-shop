package jeong.awsshop.product.service.dataimport.dto;

import jeong.awsshop.product.domain.Product;

public record DataImportProduct(String parentAsin, Product product) {
}
