package jeong.awsshop.product.repository;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import jeong.awsshop.product.repository.ProductDescriptionRepository;
import jeong.awsshop.product.repository.ProductFeatureRepository;
import jeong.awsshop.product.repository.ProductImageRepository;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductDescriptionDetailProjection;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductFeatureDetailProjection;
import jeong.awsshop.product.repository.projection.ProductImageDetailProjection;
import jeong.awsshop.product.service.dataimport.BulkInsertService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FindProductDetailRepositoryTest {

    @Autowired
    private BulkInsertService bulkInsertService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductFeatureRepository productFeatureRepository;

    @Autowired
    private ProductDescriptionRepository productDescriptionRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long detailProductId;

    private static final String PRODUCT_DETAIL_JSONL = """
            {"main_category":"Handmade","title":"Detail Fixture Product","average_rating":4.9,"rating_number":14,"price":17.99,"features":["feature-index-0","feature-index-1","feature-null"],"description":["description-index-0","description-index-1","description-null"],"images":[{"thumb":"pt01-thumb","large":"pt01-large","variant":"PT01","hi_res":"pt01-hires"},{"thumb":"main-thumb","large":"main-large","variant":"MAIN","hi_res":"main-hires"},{"thumb":"pt02-thumb","large":"pt02-large","variant":"PT02","hi_res":"pt02-hires"}],"videos":[{"title":"video-1","url":"https://example.com/video-1","user_id":"user-1"},{"title":"video-2","url":"https://example.com/video-2","user_id":"user-2"}],"store":"Green Acorn Kitchen","categories":["Handmade Products","Clothing, Shoes & Accessories","Luggage & Travel Gear"],"details":{"Department":"unisex-adult","Number of Items":1},"parent_asin":"RED_DETAIL_PRODUCT","bought_together":{"related_product_id":9000000000001,"related_product_title":"Related product","related_product_image_url":"related-image"}}
            """;

    @BeforeAll
    void bulkInsertFixture() throws Exception {
        // Given: BulkInsertService가 실패 row 파일을 만들 수 있도록 테스트 디렉토리를 준비한다
        Files.createDirectories(Path.of("aws-dataset"));

        // Given: 상세 조회 repository 테스트용 JSONL fixture를 저장한다
        ByteArrayInputStream inputStream = new ByteArrayInputStream(PRODUCT_DETAIL_JSONL.getBytes(UTF_8));
        bulkInsertService.bulkInsert(inputStream);

        // Given: 상세 조회 대상 Product id를 DB에서 확보한다
        detailProductId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE parent_asin = ?",
                Long.class,
                "RED_DETAIL_PRODUCT"
        );

        // Given: nulls last 정렬을 검증하기 위해 일부 index를 null로 만든다
        jdbcTemplate.update(
                "UPDATE product_features SET feature_index = NULL WHERE product_id = ? AND feature = ?",
                detailProductId,
                "feature-null"
        );
        jdbcTemplate.update(
                "UPDATE product_descriptions SET description_index = NULL WHERE product_id = ? AND description = ?",
                detailProductId,
                "description-null"
        );
    }

    @Test
    @DisplayName("Product id로 상세 본문 projection을 조회해야 한다")
    void should_find_product_body_by_id() {
        // Given: 상세 조회 대상 Product id

        // When: Product 본문 projection을 조회한다
        Optional<ProductDetailProjection> row = productRepository.findDetailById(detailProductId);

        // Then: Product 본문 필드가 모두 projection에 포함되어야 한다
        assertThat(row).isPresent();
        assertThat(row.get().getId()).isEqualTo(detailProductId);
        assertThat(row.get().getParentAsin()).isEqualTo("RED_DETAIL_PRODUCT");
        assertThat(row.get().getTitle()).isEqualTo("Detail Fixture Product");
        assertThat(row.get().getMainCategory()).isEqualTo("HANDMADE");
        assertThat(row.get().getAverageRating()).isEqualByComparingTo("4.9");
        assertThat(row.get().getRatingNumber()).isEqualTo(14);
        assertThat(row.get().getPrice()).isEqualByComparingTo("17.99");
        assertThat(row.get().getStore()).isEqualTo("Green Acorn Kitchen");
        assertThat(row.get().getDetails()).contains("Department");
    }

    @Test
    @DisplayName("Product id에 해당하는 상품이 없으면 빈 Optional을 반환해야 한다")
    void should_return_empty_optional_when_product_does_not_exist() {
        // Given: 존재하지 않는 Product id
        Long unknownProductId = 9_999_999_999_999L;

        // When: Product 본문 projection을 조회한다
        Optional<ProductDetailProjection> row = productRepository.findDetailById(unknownProductId);

        // Then: 조회 결과는 빈 Optional이어야 한다
        assertThat(row).isEmpty();
    }

    @Test
    @DisplayName("feature는 featureIndex ASC, nulls last, id ASC 순서로 조회해야 한다")
    void should_find_features_ordered_by_feature_index_with_nulls_last() {
        // Given: featureIndex가 있는 row와 null인 row가 섞인 상품

        // When: feature 상세 projection을 조회한다
        List<ProductFeatureDetailProjection> rows =
                productFeatureRepository.findFeatureDetailsByProductId(detailProductId);

        // Then: null index는 뒤로 가고 나머지는 index 오름차순이어야 한다
        assertThat(rows).extracting(ProductFeatureDetailProjection::getFeature)
                .containsExactly("feature-index-0", "feature-index-1", "feature-null");
        assertThat(rows).extracting(ProductFeatureDetailProjection::getFeatureIndex)
                .containsExactly(0, 1, null);
    }

    @Test
    @DisplayName("description은 descriptionIndex ASC, nulls last, id ASC 순서로 조회해야 한다")
    void should_find_descriptions_ordered_by_description_index_with_nulls_last() {
        // Given: descriptionIndex가 있는 row와 null인 row가 섞인 상품

        // When: description 상세 projection을 조회한다
        List<ProductDescriptionDetailProjection> rows =
                productDescriptionRepository.findDescriptionDetailsByProductId(detailProductId);

        // Then: null index는 뒤로 가고 나머지는 index 오름차순이어야 한다
        assertThat(rows).extracting(ProductDescriptionDetailProjection::getDescription)
                .containsExactly("description-index-0", "description-index-1", "description-null");
        assertThat(rows).extracting(ProductDescriptionDetailProjection::getDescriptionIndex)
                .containsExactly(0, 1, null);
    }

    @Test
    @DisplayName("image는 MAIN을 먼저 반환하고 이후 id ASC 순서로 조회해야 한다")
    void should_find_images_with_main_image_first() {
        // Given: PT01, MAIN, PT02 순서로 저장된 이미지

        // When: image 상세 projection을 조회한다
        List<ProductImageDetailProjection> rows =
                productImageRepository.findImageDetailsByProductId(detailProductId);

        // Then: MAIN이 첫 번째이고 나머지는 id ASC 순서여야 한다
        assertThat(rows).extracting(ProductImageDetailProjection::getVariant)
                .containsExactly("MAIN", "PT01", "PT02");
        assertThat(rows.getFirst().getThumb()).isEqualTo("main-thumb");
    }
}
