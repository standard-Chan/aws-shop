package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.domain.ProductBoughtTogether;
import jeong.awsshop.product.domain.ProductCategory;
import jeong.awsshop.product.domain.ProductDescription;
import jeong.awsshop.product.domain.ProductFeature;
import jeong.awsshop.product.domain.ProductImage;
import jeong.awsshop.product.domain.ProductVideo;
import org.springframework.stereotype.Component;

/**
 * 적재용 JSON 트리를 Product와 child entity로 조립한다.
 */
@Component
public class DataImportProductAssembler {

    /**
     * 루트 노드를 Product와 child entity로 변환한다.
     * 필수값이 없으면 null을 반환한다.
     */
    public DataImportProduct assemble(JsonNode rootNode) {
        String parentAsin = text(rootNode, DataImportJsonKey.PARENT_ASIN);
        String title = text(rootNode, DataImportJsonKey.TITLE);
        if (isBlank(parentAsin) || isBlank(title)) {
            return null;
        }

        Product product = Product.builder()
                .parentAsin(parentAsin)
                .title(title)
                .mainCategory(mapMainCategory(text(rootNode, DataImportJsonKey.MAIN_CATEGORY)))
                .averageRating(decimal(rootNode.get(DataImportJsonKey.AVERAGE_RATING.value())))
                .ratingNumber(integer(rootNode.get(DataImportJsonKey.RATING_NUMBER.value())))
                .price(decimal(rootNode.get(DataImportJsonKey.PRICE.value())))
                .store(text(rootNode, DataImportJsonKey.STORE))
                .details(details(rootNode.get(DataImportJsonKey.DETAILS.value())))
                .build();

        addFeatures(rootNode.get(DataImportJsonKey.FEATURES.value()), product);
        addDescriptions(rootNode.get(DataImportJsonKey.DESCRIPTION.value()), product);
        addCategories(rootNode.get(DataImportJsonKey.CATEGORIES.value()), product);
        addImages(rootNode.get(DataImportJsonKey.IMAGES.value()), product);
        addVideos(rootNode.get(DataImportJsonKey.VIDEOS.value()), product);
        addBoughtTogether(rootNode.get(DataImportJsonKey.BOUGHT_TOGETHER.value()), product);

        return new DataImportProduct(parentAsin, product);
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
        JsonNode relatedProductIdNode = node.get(DataImportJsonKey.RELATED_PRODUCT_ID.value());
        if (relatedProductIdNode == null || relatedProductIdNode.isNull()) {
            relatedProductIdNode = node.get(DataImportJsonKey.RELATED_PRODUCT_ID_SNAKE.value());
        }
        Long relatedProductId = longValue(relatedProductIdNode);
        if (relatedProductId == null) {
            return;
        }
        ProductBoughtTogether productBoughtTogether = ProductBoughtTogether.builder()
                .product(product)
                .relatedProductId(relatedProductId)
                .relatedProductTitle(text(node.get(DataImportJsonKey.RELATED_PRODUCT_TITLE.value())))
                .relatedProductImageUrl(text(node.get(DataImportJsonKey.RELATED_PRODUCT_IMAGE_URL.value())))
                .build();
        product.addBoughtTogether(productBoughtTogether);
    }

    private MainCategory mapMainCategory(String value) {
        return MainCategory.fromDisplayName(value);
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String text(JsonNode rootNode, DataImportJsonKey fieldName) {
        return rootNode == null ? null : text(rootNode.get(fieldName.value()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private BigDecimal decimal(JsonNode node) {
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
