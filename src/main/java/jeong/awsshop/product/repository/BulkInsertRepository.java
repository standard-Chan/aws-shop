package jeong.awsshop.product.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import jeong.awsshop.common.snowflake.SnowflakeIdGenerator;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.service.dataimport.dto.ProductDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@AllArgsConstructor
@Slf4j
public class BulkInsertRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator();

    public List<ProductDto> bulkInsert(List<ProductDto> dtos) {

        String productSql = """
            INSERT INTO product (
                id, parent_asin, title, main_category, average_rating, rating_number, price, store, details
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String featureSql = """
            INSERT INTO product_features (id, product_id, feature, feature_index)
            VALUES (?, ?, ?, ?)
        """;
        String descriptionSql = """
            INSERT INTO product_descriptions (id, product_id, description, description_index)
            VALUES (?, ?, ?, ?)
        """;
        String categorySql = """
            INSERT INTO product_categories (id, product_id, category)
            VALUES (?, ?, ?)
        """;
        String imageSql = """
            INSERT INTO product_images (id, product_id, variant, thumb, large, hi_res)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        String videoSql = """
            INSERT INTO product_videos (id, product_id, title, url, user_id)
            VALUES (?, ?, ?, ?, ?)
        """;
        String boughtTogetherSql = """
            INSERT INTO product_bought_together (
                id, product_id, related_product_id, related_product_title, related_product_image_url
            ) VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try (
                PreparedStatement productPs = conn.prepareStatement(productSql);
                PreparedStatement featurePs = conn.prepareStatement(featureSql);
                PreparedStatement descPs = conn.prepareStatement(descriptionSql);
                PreparedStatement categoryPs = conn.prepareStatement(categorySql);
                PreparedStatement imagePs = conn.prepareStatement(imageSql);
                PreparedStatement videoPs = conn.prepareStatement(videoSql);
                PreparedStatement boughtTogetherPs = conn.prepareStatement(boughtTogetherSql)
            ) {

                int batchSize = 100;
                int count = 0;

                for (ProductDto dto : dtos) {
                    long productId = idGenerator.nextId();

                    productPs.setLong(1, productId);
                    productPs.setString(2, dto.parentAsin());
                    productPs.setString(3, dto.title());
                    productPs.setString(4, mapMainCategory(dto.mainCategory()));
                    productPs.setBigDecimal(5, dto.averageRating());
                    productPs.setObject(6, dto.ratingNumber());
                    productPs.setBigDecimal(7, dto.price());
                    productPs.setString(8, dto.store());
                    productPs.setString(9, serializeDetails(dto));
                    productPs.addBatch();

                    int featureIndex = 0;
                    for (String f : dto.featuresOrEmpty()) {
                        featurePs.setLong(1, idGenerator.nextId());
                        featurePs.setLong(2, productId);
                        featurePs.setString(3, f);
                        featurePs.setInt(4, featureIndex++);
                        featurePs.addBatch();
                    }

                    int descriptionIndex = 0;
                    for (String d : dto.descriptionsOrEmpty()) {
                        descPs.setLong(1, idGenerator.nextId());
                        descPs.setLong(2, productId);
                        descPs.setString(3, d);
                        descPs.setInt(4, descriptionIndex++);
                        descPs.addBatch();
                    }

                    for (String c : dto.categoriesOrEmpty()) {
                        categoryPs.setLong(1, idGenerator.nextId());
                        categoryPs.setLong(2, productId);
                        categoryPs.setString(3, c);
                        categoryPs.addBatch();
                    }

                    for (ProductDto.ProductImageDto img : dto.imagesOrEmpty()) {
                        imagePs.setLong(1, idGenerator.nextId());
                        imagePs.setLong(2, productId);
                        imagePs.setString(3, img.variant());
                        imagePs.setString(4, img.thumb());
                        imagePs.setString(5, img.large());
                        imagePs.setString(6, img.hiRes());
                        imagePs.addBatch();
                    }

                    for (ProductDto.ProductVideoDto v : dto.videosOrEmpty()) {
                        videoPs.setLong(1, idGenerator.nextId());
                        videoPs.setLong(2, productId);
                        videoPs.setString(3, v.title());
                        videoPs.setString(4, v.url());
                        videoPs.setString(5, v.userId());
                        videoPs.addBatch();
                    }

                    if (dto.boughtTogether() != null && dto.boughtTogether().relatedProductId() != null) {
                        boughtTogetherPs.setLong(1, idGenerator.nextId());
                        boughtTogetherPs.setLong(2, productId);
                        boughtTogetherPs.setLong(3, dto.boughtTogether().relatedProductId());
                        boughtTogetherPs.setString(4, dto.boughtTogether().relatedProductTitle());
                        boughtTogetherPs.setString(5, dto.boughtTogether().relatedProductImageUrl());
                        boughtTogetherPs.addBatch();
                    }

                    if (++count % batchSize == 0) {
                        executeAll(productPs, featurePs, descPs, categoryPs, imagePs, videoPs, boughtTogetherPs);
                    }
                }

                executeAll(productPs, featurePs, descPs, categoryPs, imagePs, videoPs, boughtTogetherPs);

                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            // MySQL 중복키 등 제약조건 위반
            if (e instanceof SQLIntegrityConstraintViolationException
                || e.getErrorCode() == 1062) {
                log.info("[Bulk Insert] 유니크 키가 중복되어 insert 를 skip합니다.");
                return new ArrayList<>();
            }
            else {
                log.error("[Bulk Insert] SQL isnert 예외가 발생하였습니다", e);
                // 예외 발생 시, 해당 batch 를 반환하여 저장할 수 있도록 한다.
                return dtos;
            }
        } catch (Exception e) {
            log.error("[Bulk Insert] bulk insert 중, 예상하지 못한 에러가 발생하였습니다.", e);
            // 예외 발생 시, 해당 batch 를 반환하여 저장할 수 있도록 한다.
            return dtos;
        }

        // 성공 시, 빈 배열 반환
        return new ArrayList<>();
    }


    private void executeAll(PreparedStatement... statements) throws Exception {
        for (PreparedStatement ps : statements) {
            ps.executeBatch();
            ps.clearBatch();
        }
    }

    private String mapMainCategory(String rawMainCategory) {
        return MainCategory.fromDisplayName(rawMainCategory).name();
    }

    private String serializeDetails(ProductDto dto) {
        if (dto.details() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dto.details());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize product details", e);
        }
    }
}
