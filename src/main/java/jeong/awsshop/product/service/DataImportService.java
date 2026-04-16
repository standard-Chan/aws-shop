package jeong.awsshop.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.domain.ProductBoughtTogether;
import jeong.awsshop.product.domain.ProductCategory;
import jeong.awsshop.product.domain.ProductDescription;
import jeong.awsshop.product.domain.ProductFeature;
import jeong.awsshop.product.domain.ProductImage;
import jeong.awsshop.product.domain.ProductVideo;
import jeong.awsshop.product.exception.dataImport.DataImportPersistenceException;
import jeong.awsshop.product.exception.dataImport.DataImportDuplicateParentAsinException;
import jeong.awsshop.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대용량 데이터들을 DB에 IMPORT 하기 위한 로직
 */
@Service
public class DataImportService {

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;

    public DataImportService(ObjectMapper objectMapper, ProductRepository productRepository) {
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
    }

    @Transactional
    public void insert(String jsonLine) {
        final JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(jsonLine);
        } catch (JsonProcessingException e) {
            return;
        }

        String parentAsin = text(rootNode, "parent_asin");
        String title = text(rootNode, "title");
        if (isBlank(parentAsin) || isBlank(title)) {
            return;
        }

        if (productRepository.existsByParentAsin(parentAsin)) {
            throw new DataImportDuplicateParentAsinException(parentAsin);
        }

        Product product = Product.builder()
                .parentAsin(parentAsin)
                .title(title)
                .mainCategory(mapMainCategory(text(rootNode, "main_category")))
                .averageRating(decimal(rootNode.get("average_rating")))
                .ratingNumber(integer(rootNode.get("rating_number")))
                .price(decimal(rootNode.get("price")))
                .store(text(rootNode, "store"))
                .details(details(rootNode.get("details")))
                .build();

        addFeatures(rootNode.get("features"), product);
        addDescriptions(rootNode.get("description"), product);
        addCategories(rootNode.get("categories"), product);
        addImages(rootNode.get("images"), product);
        addVideos(rootNode.get("videos"), product);
        addBoughtTogether(rootNode.get("bought_together"), product);

        try {
            productRepository.saveAndFlush(product);
        } catch (RuntimeException e) {
            throw new DataImportPersistenceException("[DataImport insert 실패]: " + e.getMessage(), e);
        }
    }

    private void addFeatures(JsonNode node, Product product) {
        if (node == null || !node.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode item : node) {
            String feature = text(item);
            if (isBlank(feature)) {
                index++;
                continue;
            }
            ProductFeature productFeature = ProductFeature.builder()
                    .product(product)
                    .feature(feature)
                    .featureIndex(index)
                    .build();
            product.addFeature(productFeature);
            index++;
        }
    }

    private void addDescriptions(JsonNode node, Product product) {
        if (node == null || !node.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode item : node) {
            String description = text(item);
            if (isBlank(description)) {
                index++;
                continue;
            }
            ProductDescription productDescription = ProductDescription.builder()
                    .product(product)
                    .description(description)
                    .descriptionIndex(index)
                    .build();
            product.addDescription(productDescription);
            index++;
        }
    }

    private void addCategories(JsonNode node, Product product) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String category = text(item);
            if (isBlank(category)) {
                continue;
            }
            ProductCategory productCategory = ProductCategory.builder()
                    .product(product)
                    .category(category)
                    .build();
            product.addCategory(productCategory);
        }
    }

    private void addImages(JsonNode node, Product product) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            ProductImage productImage = ProductImage.builder()
                    .product(product)
                    .variant(text(item.get("variant")))
                    .thumb(text(item.get("thumb")))
                    .large(text(item.get("large")))
                    .hiRes(text(item.get("hi_res")))
                    .build();
            product.addImage(productImage);
        }
    }

    private void addVideos(JsonNode node, Product product) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            ProductVideo productVideo = ProductVideo.builder()
                    .product(product)
                    .title(text(item.get("title")))
                    .url(text(item.get("url")))
                    .userId(normalizeUserId(text(item.get("user_id"))))
                    .build();
            product.addVideo(productVideo);
        }
    }

    private void addBoughtTogether(JsonNode node, Product product) {
        if (node == null || node.isNull()) {
            return;
        }
        JsonNode relatedProductIdNode = node.get("relatedProductId");
        if (relatedProductIdNode == null || relatedProductIdNode.isNull()) {
            relatedProductIdNode = node.get("related_product_id");
        }
        Long relatedProductId = longValue(relatedProductIdNode);
        if (relatedProductId == null) {
            return;
        }
        ProductBoughtTogether productBoughtTogether = ProductBoughtTogether.builder()
                .product(product)
                .relatedProductId(relatedProductId)
                .relatedProductTitle(text(node.get("relatedProductTitle")))
                .relatedProductImageUrl(text(node.get("relatedProductImageUrl")))
                .build();
        product.addBoughtTogether(productBoughtTogether);
    }

    private jeong.awsshop.product.domain.MainCategory mapMainCategory(String value) {
        return jeong.awsshop.product.domain.MainCategory.fromDisplayName(value);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String text(JsonNode rootNode, String fieldName) {
        return rootNode == null ? null : text(rootNode.get(fieldName));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private java.math.BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.decimalValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        try {
            return Math.max(node.intValue(), 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.longValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String details(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }

    private String normalizeUserId(String value) {
        return isBlank(value) ? null : value;
    }
}
