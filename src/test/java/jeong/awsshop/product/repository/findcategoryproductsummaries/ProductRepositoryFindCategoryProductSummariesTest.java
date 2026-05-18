package jeong.awsshop.product.repository.findcategoryproductsummaries;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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
class ProductRepositoryFindCategoryProductSummariesTest {

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
            {"main_category":"Handmade","title":"Null Price Product","average_rating":4.6,"rating_number":50,"price":null,"images":[{"thumb":"null-main-thumb","large":"null-main-large","variant":"MAIN","hi_res":null}],"store":"Fixture Store","parent_asin":"PRICE_NULL","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Zero Price Product","average_rating":4.4,"rating_number":40,"price":0.00,"images":[{"thumb":"zero-main-thumb","large":"zero-main-large","variant":"MAIN","hi_res":null}],"store":"Fixture Store","parent_asin":"PRICE_ZERO","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Low Price Product","average_rating":4.3,"rating_number":30,"price":5.00,"images":[{"thumb":"low-main-thumb","large":"low-main-large","variant":"MAIN","hi_res":null}],"store":"Fixture Store","parent_asin":"PRICE_LOW","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Same Low Price Product","average_rating":4.2,"rating_number":20,"price":5.00,"images":[{"thumb":"same-low-main-thumb","large":"same-low-main-large","variant":"MAIN","hi_res":null}],"store":"Fixture Store","parent_asin":"PRICE_SAME_LOW","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"High Price Product","average_rating":4.1,"rating_number":10,"price":99.99,"images":[{"thumb":"high-main-thumb","large":"high-main-large","variant":"MAIN","hi_res":null}],"store":"Fixture Store","parent_asin":"PRICE_HIGH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
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
    @DisplayName("category 조건에 맞는 상품만 조회해야 한다")
    void should_find_products_by_category_when_category_exists() {
        // Given: Handmade와 Gift Cards fixture가 함께 저장되어 있다

        // When: Handmade category만 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByAverageRating(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: 모든 row는 Handmade category여야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getMainCategory()).isEqualTo("HANDMADE")
        );
    }

    @Test
    @DisplayName("averageRating 및 정렬을 average_rating DESC로 한 경우, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_average_rating_desc_and_id_asc_when_cursor_is_null() {
        // Given: 같은 category 안에 서로 다른 averageRating과 같은 averageRating 상품이 있다

        // When: averageRating 기준 첫 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByAverageRating(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: averageRating은 내림차순이어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getAverageRating)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));

        // Then: 같은 averageRating 4.0 상품은 id 오름차순이어야 한다
        assertThat(rows.stream()
                .filter(row -> row.getAverageRating().compareTo(new BigDecimal("4.0")) == 0)
                .map(ProductSummaryNativeProjection::getId)
                .toList()).isSorted();
    }

    @Test
    @DisplayName("averageRating cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_average_rating_cursor_when_cursor_exists() {
        // Given: averageRating 첫 페이지에서 두 번째 row를 cursor로 선택한다
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findCategoryProductSummariesOrderByAverageRating(
                        "HANDMADE",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 averageRating 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByAverageRating(
                        "HANDMADE",
                        cursor.getId(),
                        cursor.getAverageRating(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            boolean lowerRating = row.getAverageRating().compareTo(cursor.getAverageRating()) < 0;
            boolean sameRatingAndGreaterId = row.getAverageRating().compareTo(cursor.getAverageRating()) == 0
                    && row.getId() > cursor.getId();
            assertThat(lowerRating || sameRatingAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("averageRating 정렬에서는 price NULL 상품도 조회해야 한다")
    void should_include_null_price_products_when_sorting_by_average_rating() {
        // Given: Handmade category 안에 price가 NULL인 상품이 있다

        // When: averageRating 기준 첫 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByAverageRating(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: price NULL 상품도 averageRating 정렬 대상에는 포함되어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("PRICE_NULL");
    }

    @Test
    @DisplayName("ratingNumber 첫 페이지는 rating_number DESC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_rating_number_desc_and_id_asc_when_cursor_is_null() {
        // Given: 같은 category 안에 서로 다른 ratingNumber와 같은 ratingNumber 상품이 있다

        // When: ratingNumber 기준 첫 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByRatingNumber(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: ratingNumber는 내림차순이어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getRatingNumber)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));

        // Then: 같은 ratingNumber 10 상품은 id 오름차순이어야 한다
        assertThat(rows.stream()
                .filter(row -> row.getRatingNumber().equals(10))
                .map(ProductSummaryNativeProjection::getId)
                .toList()).isSorted();
    }

    @Test
    @DisplayName("ratingNumber cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_rating_number_cursor_when_cursor_exists() {
        // Given: ratingNumber 첫 페이지에서 두 번째 row를 cursor로 선택한다
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findCategoryProductSummariesOrderByRatingNumber(
                        "HANDMADE",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 ratingNumber 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByRatingNumber(
                        "HANDMADE",
                        cursor.getId(),
                        cursor.getRatingNumber(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            boolean lowerRatingNumber = row.getRatingNumber() < cursor.getRatingNumber();
            boolean sameRatingNumberAndGreaterId = row.getRatingNumber().equals(cursor.getRatingNumber())
                    && row.getId() > cursor.getId();
            assertThat(lowerRatingNumber || sameRatingNumberAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("price ASC 첫 페이지는 price NULL을 제외하고 price ASC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_price_asc_and_id_asc_when_cursor_is_null() {
        // Given: 같은 category 안에 null, 0, 양수 price 상품이 있다

        // When: price ASC 기준 첫 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByPriceAsc(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: price NULL 상품은 제외하고, price 0 상품은 포함해야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("PRICE_NULL")
                .contains("PRICE_ZERO");

        // Then: price는 오름차순이어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getPrice)
                .isSortedAccordingTo(BigDecimal::compareTo);

        // Then: 같은 price 5.00 상품은 id 오름차순이어야 한다
        assertThat(rows.stream()
                .filter(row -> row.getPrice().compareTo(new BigDecimal("5.00")) == 0)
                .map(ProductSummaryNativeProjection::getId)
                .toList()).isSorted();
    }

    @Test
    @DisplayName("price DESC 첫 페이지는 price NULL을 제외하고 price DESC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_price_desc_and_id_asc_when_cursor_is_null() {
        // Given: 같은 category 안에 null, 0, 양수 price 상품이 있다

        // When: price DESC 기준 첫 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByPriceDesc(
                        "HANDMADE",
                        null,
                        null,
                        20
                );

        // Then: price NULL 상품은 제외하고, price 0 상품은 포함해야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("PRICE_NULL")
                .contains("PRICE_ZERO");

        // Then: price는 내림차순이어야 한다
        assertThat(rows).extracting(ProductSummaryNativeProjection::getPrice)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));

        // Then: 같은 price 5.00 상품은 id 오름차순이어야 한다
        assertThat(rows.stream()
                .filter(row -> row.getPrice().compareTo(new BigDecimal("5.00")) == 0)
                .map(ProductSummaryNativeProjection::getId)
                .toList()).isSorted();
    }

    @Test
    @DisplayName("price ASC cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_price_asc_cursor_when_cursor_exists() {
        // Given: price ASC 첫 페이지에서 두 번째 row를 cursor로 선택한다
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findCategoryProductSummariesOrderByPriceAsc(
                        "HANDMADE",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 price ASC 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByPriceAsc(
                        "HANDMADE",
                        cursor.getId(),
                        cursor.getPrice(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            boolean higherPrice = row.getPrice().compareTo(cursor.getPrice()) > 0;
            boolean samePriceAndGreaterId = row.getPrice().compareTo(cursor.getPrice()) == 0
                    && row.getId() > cursor.getId();
            assertThat(higherPrice || samePriceAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("price DESC cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_price_desc_cursor_when_cursor_exists() {
        // Given: price DESC 첫 페이지에서 두 번째 row를 cursor로 선택한다
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findCategoryProductSummariesOrderByPriceDesc(
                        "HANDMADE",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 price DESC 페이지를 조회한다
        List<ProductSummaryNativeProjection> rows =
                productRepository.findCategoryProductSummariesOrderByPriceDesc(
                        "HANDMADE",
                        cursor.getId(),
                        cursor.getPrice(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            boolean lowerPrice = row.getPrice().compareTo(cursor.getPrice()) < 0;
            boolean samePriceAndGreaterId = row.getPrice().compareTo(cursor.getPrice()) == 0
                    && row.getId() > cursor.getId();
            assertThat(lowerPrice || samePriceAndGreaterId).isTrue();
        });
    }

    private ProductSummaryNativeProjection findByParentAsin(String parentAsin) {
        return productRepository.findProductSummaries(null, 20)
                .stream()
                .filter(row -> row.getParentAsin().equals(parentAsin))
                .findFirst()
                .orElseThrow();
    }
}
