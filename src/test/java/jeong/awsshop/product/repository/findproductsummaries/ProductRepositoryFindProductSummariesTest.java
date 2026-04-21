package jeong.awsshop.product.repository.findproductsummaries;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.dataimport.BulkInsertService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductRepositoryFindProductSummariesTest {

    @Autowired
    private BulkInsertService bulkInsertService;

    @Autowired
    private ProductRepository productRepository;

    private static final String PRODUCT_CURSOR_JSONL = """
            {"main_category":"Gift Cards","title":"Amazon.com Gift Card in Gift Tag (Various Designs)","average_rating":4.8,"rating_number":1006,"price":null,"images":[{"thumb":"gift-main-thumb","large":"gift-main-large","variant":"MAIN","hi_res":"gift-main-hires"},{"thumb":"gift-pt01-thumb","large":"gift-pt01-large","variant":"PT01","hi_res":"gift-pt01-hires"}],"store":"Amazon","parent_asin":"RED_GIFT_CARD","features":[],"description":[],"categories":["Gift Cards"],"details":{},"videos":[],"bought_together":null}
            {"main_category":"SUBSCRIPTION BOXES","title":"Loved Again Media - Movie Subscription Box - 10 DVD Box - Pick Your Genres","average_rating":4.1,"rating_number":75,"price":null,"images":[{"thumb":"movie-main-thumb","large":"movie-main-large","variant":"MAIN","hi_res":"movie-main-hires"},{"thumb":"movie-pt11-thumb","large":"movie-pt11-large","variant":"PT11","hi_res":"movie-pt11-hires"}],"store":"Loved Again Media","parent_asin":"RED_MOVIE_BOX","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Daisy Keychain Wristlet Gray Fabric Key fob Lanyard","average_rating":4.5,"rating_number":12,"price":null,"images":[{"thumb":"daisy-main-thumb","large":"daisy-main-large","variant":"MAIN","hi_res":null},{"thumb":"daisy-pt01-thumb","large":"daisy-pt01-large","variant":"PT01","hi_res":null}],"store":"Generic","parent_asin":"RED_DAISY_KEYCHAIN","features":[],"description":[],"categories":["Handmade Products"],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Silver Triangle Earrings with Chevron Pattern","average_rating":5.0,"rating_number":1,"price":null,"images":[{"thumb":"silver-main-thumb","large":"silver-main-large","variant":"MAIN","hi_res":"silver-main-hires"},{"thumb":"silver-pt01-thumb","large":"silver-pt01-large","variant":"PT01","hi_res":"silver-pt01-hires"}],"store":"Zoe Noelle Designs","parent_asin":"RED_SILVER_EARRINGS","features":[],"description":[],"categories":["Handmade Products"],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"No Main Image Product","average_rating":3.9,"rating_number":8,"price":12.50,"images":[{"thumb":"no-main-first-thumb","large":"no-main-first-large","variant":"PT01","hi_res":"no-main-first-hires"},{"thumb":"no-main-second-thumb","large":"no-main-second-large","variant":"PT02","hi_res":"no-main-second-hires"}],"store":"Fixture Store","parent_asin":"RED_NO_MAIN","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"No Image Product","average_rating":2.0,"rating_number":1,"price":7.00,"images":[],"store":"Fixture Store","parent_asin":"RED_NO_IMAGE","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Cursor Fixture 01","average_rating":4.0,"rating_number":10,"price":1.00,"images":[{"thumb":"cursor-01-main-thumb","large":"cursor-01-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"RED_CURSOR_01","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Cursor Fixture 02","average_rating":4.0,"rating_number":10,"price":2.00,"images":[{"thumb":"cursor-02-main-thumb","large":"cursor-02-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"RED_CURSOR_02","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Cursor Fixture 03","average_rating":4.0,"rating_number":10,"price":3.00,"images":[{"thumb":"cursor-03-main-thumb","large":"cursor-03-main-large","variant":"MAIN","hi_res":null}],"store":"Cursor Store","parent_asin":"RED_CURSOR_03","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            """;

    @BeforeAll
    void bulkInsertFixture() throws Exception {
        // Given: BulkInsertService가 실패 row 파일을 만들 수 있도록 테스트 디렉토리를 준비한다
        Files.createDirectories(Path.of("aws-dataset"));

        // Given: 모든 repository 테스트에서 공유할 JSONL fixture를 한 번만 저장한다
        ByteArrayInputStream inputStream = new ByteArrayInputStream(PRODUCT_CURSOR_JSONL.getBytes(UTF_8));
        bulkInsertService.bulkInsert(inputStream);
    }

    @Test
    @DisplayName("cursor가 없으면 id ASC 순서로 상품을 조회해야 한다")
    void should_find_products_ordered_by_id_asc_when_cursor_is_null() {
        // Given: 저장된 fixture 데이터

        // When: cursor 없이 충분한 limit으로 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(null, 20);

        // Then: 조회 결과는 id 오름차순이어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getId)
                .isSorted();
    }

    @Test
    @DisplayName("cursor가 있으면 cursor보다 큰 id의 상품만 조회해야 한다")
    void should_find_products_after_cursor_when_cursor_exists() {
        // Given: 저장된 상품 중 두 번째 상품 id를 cursor로 선택한다
        List<ProductSummaryNativeProjection> allRows =
                productRepository.findProductSummaries(null, 20);
        Long cursor = allRows.get(1).getId();

        // When: cursor 이후 상품을 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(cursor, 20);

        // Then: 모든 결과는 cursor보다 큰 id를 가져야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getId()).isGreaterThan(cursor)
        );
    }

    @Test
    @DisplayName("MAIN 이미지가 있으면 대표 image로 MAIN을 반환해야 한다")
    void should_select_main_image_when_main_image_exists() {
        // Given: MAIN과 PT01 이미지를 모두 가진 상품

        // When: 상품 목록을 조회한다
        ProductSummaryNativeProjection row = findByParentAsin("RED_GIFT_CARD");

        // Then: 대표 이미지는 MAIN이어야 한다
        assertThat(row.getImageVariant()).isEqualTo("MAIN");
        assertThat(row.getImageThumb()).isEqualTo("gift-main-thumb");
        assertThat(row.getImageLarge()).isEqualTo("gift-main-large");
        assertThat(row.getImageHiRes()).isEqualTo("gift-main-hires");
    }

    @Test
    @DisplayName("MAIN 이미지가 없으면 ProductImage id ASC 기준 첫 번째 이미지를 반환해야 한다")
    void should_select_first_image_by_id_when_main_image_does_not_exist() {
        // Given: PT01, PT02만 가진 상품. 먼저 저장된 PT01이 대표 이미지가 되어야 한다

        // When: 상품 목록을 조회한다
        ProductSummaryNativeProjection row = findByParentAsin("RED_NO_MAIN");

        // Then: MAIN이 없으면 첫 번째 저장 이미지인 PT01을 반환해야 한다
        assertThat(row.getImageVariant()).isEqualTo("PT01");
        assertThat(row.getImageThumb()).isEqualTo("no-main-first-thumb");
        assertThat(row.getImageLarge()).isEqualTo("no-main-first-large");
        assertThat(row.getImageHiRes()).isEqualTo("no-main-first-hires");
    }

    @Test
    @DisplayName("이미지가 없어도 Product는 조회되고 image 필드는 null이어야 한다")
    void should_return_product_with_null_image_when_product_has_no_image() {
        // Given: images가 빈 배열인 상품

        // When: 상품 목록을 조회한다
        ProductSummaryNativeProjection row = findByParentAsin("RED_NO_IMAGE");

        // Then: LEFT JOIN 결과로 상품은 유지되고 image 값만 null이어야 한다
        assertThat(row.getTitle()).isEqualTo("No Image Product");
        assertThat(row.getImageVariant()).isNull();
        assertThat(row.getImageThumb()).isNull();
        assertThat(row.getImageLarge()).isNull();
        assertThat(row.getImageHiRes()).isNull();
    }

    @Test
    @DisplayName("이미지가 여러 개여도 Product row는 1개만 반환되어야 한다")
    void should_return_only_one_representative_image_per_product_when_product_has_multiple_images() {
        // Given: 여러 이미지를 가진 상품

        // When: 상품 목록을 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findProductSummaries(null, 20);

        // Then: 같은 parentAsin의 Product row는 하나만 있어야 한다
        assertThat(rows)
                .filteredOn(row -> row.getParentAsin().equals("RED_GIFT_CARD"))
                .hasSize(1);
    }

    private ProductSummaryNativeProjection findByParentAsin(String parentAsin) {
        return productRepository.findProductSummaries(null, 20)
                .stream()
                .filter(row -> row.getParentAsin().equals(parentAsin))
                .findFirst()
                .orElseThrow();
    }
}
