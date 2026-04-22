package jeong.awsshop.product.service.dataimport;

import com.fasterxml.jackson.databind.JsonNode;
import static jeong.awsshop.common.json.JsonNodeValues.decimal;
import static jeong.awsshop.common.json.JsonNodeValues.details;
import static jeong.awsshop.common.json.JsonNodeValues.integer;
import static jeong.awsshop.common.json.JsonNodeValues.isBlank;
import static jeong.awsshop.common.json.JsonNodeValues.longValue;
import static jeong.awsshop.common.json.JsonNodeValues.text;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.domain.Product;
import jeong.awsshop.product.domain.ProductBoughtTogether;
import jeong.awsshop.product.domain.ProductCategory;
import jeong.awsshop.product.domain.ProductDescription;
import jeong.awsshop.product.domain.ProductFeature;
import jeong.awsshop.product.domain.ProductImage;
import jeong.awsshop.product.domain.ProductVideo;
import jeong.awsshop.product.service.dataimport.dto.DataImportProduct;
import org.springframework.stereotype.Component;

/**
 * 적재용 JSON 트리를 Product와 child entity로 조립한다.
 */
@Component
public class DataImportProductAssembler {

    private final SnowflakeIdGenerator idGenerator;

    public DataImportProductAssembler(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * 루트 노드를 Product와 child entity로 변환한다.
     * 필수값이 없으면 null을 반환한다.
     */
    public DataImportProduct assemble(JsonNode rootNode) {
        String parentAsin = fieldText(rootNode, DataImportJsonKey.PARENT_ASIN);
        String title = fieldText(rootNode, DataImportJsonKey.TITLE);
        if (isBlank(parentAsin) || isBlank(title)) {
            return null;
        }

        Product product = Product.builder()
                .id(idGenerator.nextId())
                .parentAsin(parentAsin)
                .title(title)
                .mainCategory(mapMainCategory(fieldText(rootNode, DataImportJsonKey.MAIN_CATEGORY)))
                .averageRating(decimal(rootNode.get(DataImportJsonKey.AVERAGE_RATING.value())))
                .ratingNumber(integer(rootNode.get(DataImportJsonKey.RATING_NUMBER.value())))
                .price(decimal(rootNode.get(DataImportJsonKey.PRICE.value())))
                .store(fieldText(rootNode, DataImportJsonKey.STORE))
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
            product.addFeature(ProductFeature.of(idGenerator.nextId(), product, feature, index));
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
            product.addDescription(ProductDescription.of(idGenerator.nextId(), product, description, index));
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
            product.addCategory(ProductCategory.of(idGenerator.nextId(), product, category));
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
            product.addImage(ProductImage.of(
                    idGenerator.nextId(),
                    product,
                    text(item.get("variant")),
                    text(item.get("thumb")),
                    text(item.get("large")),
                    text(item.get("hi_res"))));
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
            product.addVideo(ProductVideo.of(
                    idGenerator.nextId(),
                    product,
                    text(item.get("title")),
                    text(item.get("url")),
                    text(item.get("user_id"))));
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
        product.addBoughtTogether(ProductBoughtTogether.of(
                idGenerator.nextId(),
                product,
                relatedProductId,
                text(node.get(DataImportJsonKey.RELATED_PRODUCT_TITLE.value())),
                text(node.get(DataImportJsonKey.RELATED_PRODUCT_IMAGE_URL.value()))));
    }

    private MainCategory mapMainCategory(String value) {
        return MainCategory.fromDisplayName(value);
    }

    private String fieldText(JsonNode rootNode, DataImportJsonKey fieldName) {
        return rootNode == null ? null : text(rootNode.get(fieldName.value()));
    }

}
