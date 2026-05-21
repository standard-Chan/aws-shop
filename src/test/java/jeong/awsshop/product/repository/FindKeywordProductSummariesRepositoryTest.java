package jeong.awsshop.product.repository;

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
class FindKeywordProductSummariesRepositoryTest {

    @Autowired
    private BulkInsertService bulkInsertService;

    @Autowired
    private ProductRepository productRepository;

    private static final String PRODUCT_CURSOR_JSONL = """
            {"main_category":"Handmade","title":"Silver Wire Basket","average_rating":4.9,"rating_number":80,"price":9.99,"images":[{"thumb":"wire-main-thumb","large":"wire-main-large","variant":"MAIN","hi_res":"wire-main-hires"}],"store":"Fixture Store","parent_asin":"WIRE_MAIN","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"WIRE Organizer Tray","average_rating":4.7,"rating_number":60,"price":12.99,"images":[{"thumb":"tray-main-thumb","large":"tray-main-large","variant":"MAIN","hi_res":"tray-main-hires"}],"store":"Fixture Store","parent_asin":"WIRE_UPPER","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Travel Wire Pouch","average_rating":4.5,"rating_number":40,"price":14.99,"images":[{"thumb":"pouch-thumb","large":"pouch-large","variant":"PT01","hi_res":"pouch-hires"},{"thumb":"pouch-thumb-2","large":"pouch-large-2","variant":"PT02","hi_res":"pouch-hires-2"}],"store":"Fixture Store","parent_asin":"WIRE_NO_MAIN","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Desk Lamp","average_rating":4.8,"rating_number":55,"price":19.99,"images":[{"thumb":"lamp-main-thumb","large":"lamp-main-large","variant":"MAIN","hi_res":"lamp-main-hires"}],"store":"Fixture Store","parent_asin":"NO_MATCH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"100! Cotton Bag","average_rating":4.6,"rating_number":30,"price":15.00,"images":[{"thumb":"bang-main-thumb","large":"bang-main-large","variant":"MAIN","hi_res":"bang-main-hires"}],"store":"Fixture Store","parent_asin":"EXCLAMATION_MATCH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"A@Home Notebook","average_rating":4.4,"rating_number":20,"price":17.00,"images":[],"store":"Fixture Store","parent_asin":"AT_MATCH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"100% Cotton Bag","average_rating":4.3,"rating_number":10,"price":18.00,"images":[{"thumb":"percent-main-thumb","large":"percent-main-large","variant":"MAIN","hi_res":"percent-main-hires"}],"store":"Fixture Store","parent_asin":"PERCENT_MATCH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"A_B Testing Notebook","average_rating":4.2,"rating_number":9,"price":19.00,"images":[{"thumb":"underscore-main-thumb","large":"underscore-main-large","variant":"MAIN","hi_res":"underscore-main-hires"}],"store":"Fixture Store","parent_asin":"UNDERSCORE_MATCH","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Wire Null Average","average_rating":null,"rating_number":33,"price":21.00,"images":[{"thumb":"null-avg-thumb","large":"null-avg-large","variant":"MAIN","hi_res":"null-avg-hires"}],"store":"Fixture Store","parent_asin":"WIRE_NULL_AVG","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Wire Null Rating","average_rating":4.0,"rating_number":null,"price":22.00,"images":[{"thumb":"null-rating-thumb","large":"null-rating-large","variant":"MAIN","hi_res":"null-rating-hires"}],"store":"Fixture Store","parent_asin":"WIRE_NULL_RATING","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            {"main_category":"Handmade","title":"Wire Null Price","average_rating":3.9,"rating_number":8,"price":null,"images":[{"thumb":"null-price-thumb","large":"null-price-large","variant":"MAIN","hi_res":"null-price-hires"}],"store":"Fixture Store","parent_asin":"WIRE_NULL_PRICE","features":[],"description":[],"categories":[],"details":{},"videos":[],"bought_together":null}
            """;

    @BeforeAll
    void bulkInsertFixture() throws Exception {
        // Given: BulkInsertService가 실패 row 파일을 만들 수 있도록 테스트 디렉토리를 준비한다.
        Files.createDirectories(Path.of("aws-dataset"));

        // Given: keyword repository 테스트용 JSONL fixture를 한 번 저장한다.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(PRODUCT_CURSOR_JSONL.getBytes(UTF_8));
        bulkInsertService.bulkInsert(inputStream);
    }

    @Test
    @DisplayName("title에 keyword가 포함된 상품만 조회해야 한다")
    void should_find_products_by_keyword_when_title_contains_keyword() {
        // Given: wire를 포함하는 title과 포함하지 않는 title이 함께 저장되어 있다.

        // When: wire keyword로 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: 모든 결과는 title에 wire를 포함해야 한다.
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getTitle().toLowerCase()).contains("wire")
        );
    }

    @Test
    @DisplayName("keyword 검색은 대소문자를 구분하지 않아야 한다")
    void should_find_products_case_insensitively_when_title_matches_keyword() {
        // Given: 대문자 title과 소문자 keyword가 존재한다.

        // When: 소문자 keyword로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: 대문자 WIRE title도 검색되어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("WIRE_UPPER");
    }

    @Test
    @DisplayName("특수문자가 들어오면 escape 처리된 문자열 기준으로 조회해야 한다")
    void should_escape_special_characters_in_keyword_before_search() {
        // Given: %, _, !, @ 문자를 포함한 title이 저장되어 있다.

        // When: 특수문자가 포함된 keyword로 각각 검색한다.
        List<ProductSummaryNativeProjection> percentRows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "100%",
                        null,
                        null,
                        20
                );
        List<ProductSummaryNativeProjection> underscoreRows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "A_B",
                        null,
                        null,
                        20
                );
        List<ProductSummaryNativeProjection> bangRows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "100!",
                        null,
                        null,
                        20
                );
        List<ProductSummaryNativeProjection> atRows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "A@",
                        null,
                        null,
                        20
                );

        // Then: literal 문자 기준으로 각각 정확한 상품만 조회되어야 한다.
        assertThat(percentRows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("PERCENT_MATCH")
                .doesNotContain("EXCLAMATION_MATCH");
        assertThat(underscoreRows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("UNDERSCORE_MATCH");
        assertThat(bangRows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("EXCLAMATION_MATCH")
                .doesNotContain("PERCENT_MATCH");
        assertThat(atRows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("AT_MATCH");
    }

    @Test
    @DisplayName("keyword가 title 중간에 있어도 조회해야 한다")
    void should_find_products_when_keyword_is_in_the_middle_of_title() {
        // Given: keyword가 title 중간에 있는 상품이 저장되어 있다.

        // When: wire keyword로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: prefix나 suffix가 아니라 중간 포함 상품도 결과에 있어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .contains("WIRE_MAIN", "WIRE_NO_MAIN");
    }

    @Test
    @DisplayName("keyword를 포함하지 않는 상품은 제외해야 한다")
    void should_exclude_products_that_do_not_contain_keyword() {
        // Given: wire와 무관한 title 상품이 함께 저장되어 있다.

        // When: wire keyword로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: 일치하지 않는 상품은 결과에서 제외되어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("NO_MATCH");
    }

    @Test
    @DisplayName("averageRating 정렬에서는 averageRating이 null인 상품을 제외해야 한다")
    void should_exclude_null_average_rating_products_when_sorting_by_average_rating() {
        // Given: 같은 keyword 결과 집합에 averageRating이 null인 상품이 있다.

        // When: averageRating 기준으로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: averageRating이 null인 상품은 조회되면 안 된다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("WIRE_NULL_AVG");
    }

    @Test
    @DisplayName("ratingNumber 정렬에서는 ratingNumber가 null인 상품을 제외해야 한다")
    void should_exclude_null_rating_number_products_when_sorting_by_rating_number() {
        // Given: 같은 keyword 결과 집합에 ratingNumber가 null인 상품이 있다.

        // When: ratingNumber 기준으로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByRatingNumber(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: ratingNumber가 null인 상품은 조회되면 안 된다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("WIRE_NULL_RATING");
    }

    @Test
    @DisplayName("price 정렬에서는 price가 null인 상품을 제외해야 한다")
    void should_exclude_null_price_products_when_sorting_by_price() {
        // Given: 같은 keyword 결과 집합에 price가 null인 상품이 있다.

        // When: price ASC 기준으로 검색한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByPriceAsc(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: price가 null인 상품은 조회되면 안 된다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getParentAsin)
                .doesNotContain("WIRE_NULL_PRICE");
    }

    @Test
    @DisplayName("averageRating 첫 페이지는 average_rating DESC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_average_rating_desc_and_id_asc_when_keyword_cursor_is_null() {
        // Given: 같은 keyword 결과 집합에 서로 다른 averageRating과 같은 averageRating 상품이 있다.

        // When: averageRating 기준 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: averageRating은 내림차순이어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getAverageRating)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("averageRating ASC 첫 페이지는 average_rating ASC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_average_rating_asc_and_id_asc_when_keyword_cursor_is_null() {
        // Given: 같은 keyword 결과 집합에 서로 다른 averageRating과 같은 averageRating 상품이 있다.

        // When: averageRating ASC 기준 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRatingAsc(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: averageRating은 오름차순이어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getAverageRating)
                .isSortedAccordingTo(BigDecimal::compareTo);
    }

    @Test
    @DisplayName("ratingNumber 첫 페이지는 rating_number DESC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_rating_number_desc_and_id_asc_when_keyword_cursor_is_null() {
        // Given: 같은 keyword 결과 집합에 서로 다른 ratingNumber 상품이 있다.

        // When: ratingNumber 기준 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByRatingNumber(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: ratingNumber는 내림차순이어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getRatingNumber)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("price ASC 첫 페이지는 price ASC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_price_asc_and_id_asc_when_keyword_cursor_is_null() {
        // Given: 같은 keyword 결과 집합에 여러 price 상품이 있다.

        // When: price ASC 기준 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByPriceAsc(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: price는 오름차순이어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getPrice)
                .isSortedAccordingTo(BigDecimal::compareTo);
    }

    @Test
    @DisplayName("price DESC 첫 페이지는 price DESC, id ASC로 조회해야 한다")
    void should_find_products_ordered_by_price_desc_and_id_asc_when_keyword_cursor_is_null() {
        // Given: 같은 keyword 결과 집합에 여러 price 상품이 있다.

        // When: price DESC 기준 첫 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByPriceDesc(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: price는 내림차순이어야 한다.
        assertThat(rows).extracting(ProductSummaryNativeProjection::getPrice)
                .isSortedAccordingTo((left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("averageRating cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_average_rating_cursor_when_keyword_cursor_exists() {
        // Given: averageRating 첫 페이지에서 두 번째 row를 cursor로 선택한다.
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 averageRating 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        cursor.getId(),
                        cursor.getAverageRating(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다.
        assertThat(rows).allSatisfy(row -> {
            boolean lowerRating = row.getAverageRating().compareTo(cursor.getAverageRating()) < 0;
            boolean sameRatingAndGreaterId = row.getAverageRating().compareTo(cursor.getAverageRating()) == 0
                    && row.getId() > cursor.getId();
            assertThat(lowerRating || sameRatingAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("averageRating ASC cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_average_rating_asc_cursor_when_keyword_cursor_exists() {
        // Given: averageRating ASC 첫 페이지에서 두 번째 row를 cursor로 선택한다.
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findKeywordProductSummariesOrderByAverageRatingAsc(
                        "wire",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 averageRating ASC 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRatingAsc(
                        "wire",
                        cursor.getId(),
                        cursor.getAverageRating(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다.
        assertThat(rows).allSatisfy(row -> {
            boolean higherRating = row.getAverageRating().compareTo(cursor.getAverageRating()) > 0;
            boolean sameRatingAndGreaterId = row.getAverageRating().compareTo(cursor.getAverageRating()) == 0
                    && row.getId() > cursor.getId();
            assertThat(higherRating || sameRatingAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("ratingNumber cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_rating_number_cursor_when_keyword_cursor_exists() {
        // Given: ratingNumber 첫 페이지에서 두 번째 row를 cursor로 선택한다.
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findKeywordProductSummariesOrderByRatingNumber(
                        "wire",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 ratingNumber 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByRatingNumber(
                        "wire",
                        cursor.getId(),
                        cursor.getRatingNumber(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다.
        assertThat(rows).allSatisfy(row -> {
            boolean lowerRatingNumber = row.getRatingNumber() < cursor.getRatingNumber();
            boolean sameRatingNumberAndGreaterId = row.getRatingNumber().equals(cursor.getRatingNumber())
                    && row.getId() > cursor.getId();
            assertThat(lowerRatingNumber || sameRatingNumberAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("price ASC cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_price_asc_cursor_when_keyword_cursor_exists() {
        // Given: price ASC 첫 페이지에서 두 번째 row를 cursor로 선택한다.
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findKeywordProductSummariesOrderByPriceAsc(
                        "wire",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 price ASC 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByPriceAsc(
                        "wire",
                        cursor.getId(),
                        cursor.getPrice(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다.
        assertThat(rows).allSatisfy(row -> {
            boolean higherPrice = row.getPrice().compareTo(cursor.getPrice()) > 0;
            boolean samePriceAndGreaterId = row.getPrice().compareTo(cursor.getPrice()) == 0
                    && row.getId() > cursor.getId();
            assertThat(higherPrice || samePriceAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("price DESC cursor가 있으면 cursor 이후 페이지를 조회해야 한다")
    void should_find_next_products_by_price_desc_cursor_when_keyword_cursor_exists() {
        // Given: price DESC 첫 페이지에서 두 번째 row를 cursor로 선택한다.
        List<ProductSummaryNativeProjection> firstPage =
                productRepository.findKeywordProductSummariesOrderByPriceDesc(
                        "wire",
                        null,
                        null,
                        20
                );
        ProductSummaryNativeProjection cursor = firstPage.get(1);

        // When: cursor 이후 price DESC 페이지를 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByPriceDesc(
                        "wire",
                        cursor.getId(),
                        cursor.getPrice(),
                        20
                );

        // Then: 결과는 cursor 정렬 위치 이후의 상품이어야 한다.
        assertThat(rows).allSatisfy(row -> {
            boolean lowerPrice = row.getPrice().compareTo(cursor.getPrice()) < 0;
            boolean samePriceAndGreaterId = row.getPrice().compareTo(cursor.getPrice()) == 0
                    && row.getId() > cursor.getId();
            assertThat(lowerPrice || samePriceAndGreaterId).isTrue();
        });
    }

    @Test
    @DisplayName("대표 이미지가 MAIN이면 해당 이미지를 사용해야 한다")
    void should_select_main_image_first_when_keyword_products_have_main_image() {
        // Given: MAIN 이미지가 있는 keyword 결과 상품이 있다.

        // When: wire keyword로 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: MAIN 이미지가 대표 이미지로 선택되어야 한다.
        ProductSummaryNativeProjection row = rows.stream()
                .filter(candidate -> "WIRE_MAIN".equals(candidate.getParentAsin()))
                .findFirst()
                .orElseThrow();
        assertThat(row.getImageVariant()).isEqualTo("MAIN");
    }

    @Test
    @DisplayName("MAIN 이미지가 없으면 첫 번째 이미지를 사용해야 한다")
    void should_select_first_image_when_main_image_does_not_exist() {
        // Given: MAIN 이미지가 없는 keyword 결과 상품이 있다.

        // When: wire keyword로 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "wire",
                        null,
                        null,
                        20
                );

        // Then: id가 가장 작은 이미지가 대표 이미지로 선택되어야 한다.
        ProductSummaryNativeProjection row = rows.stream()
                .filter(candidate -> "WIRE_NO_MAIN".equals(candidate.getParentAsin()))
                .findFirst()
                .orElseThrow();
        assertThat(row.getImageVariant()).isEqualTo("PT01");
    }

    @Test
    @DisplayName("이미지가 없으면 image 필드는 null이어야 한다")
    void should_return_null_image_when_keyword_product_has_no_image() {
        // Given: 이미지가 없는 keyword 결과 상품이 있다.

        // When: @ keyword로 조회한다.
        List<ProductSummaryNativeProjection> rows =
                productRepository.findKeywordProductSummariesOrderByAverageRating(
                        "@",
                        null,
                        null,
                        20
                );

        // Then: image 관련 필드는 null이어야 한다.
        ProductSummaryNativeProjection row = rows.getFirst();
        assertThat(row.getImageVariant()).isNull();
        assertThat(row.getImageThumb()).isNull();
        assertThat(row.getImageLarge()).isNull();
        assertThat(row.getImageHiRes()).isNull();
    }
}
