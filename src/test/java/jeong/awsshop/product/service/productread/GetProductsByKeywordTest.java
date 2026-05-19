package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductDetailProjection;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GetProductsByKeywordTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductReadService productReadService;

    @Test
    @DisplayName("sort가 없으면 averageRating을 기본 정렬로 사용해야 한다")
    void should_use_average_rating_sort_when_sort_is_absent() {
        // Given: keyword 검색 결과가 averageRating 기준으로 1개 존재한다.
        when(productRepository.findKeywordProductSummariesOrderByAverageRating(
                "wire",
                null,
                null,
                2
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.8"), 50, new BigDecimal("19.99"))
        ));

        // When: sort 없이 keyword 검색을 수행한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "wire",
                1,
                null,
                null,
                null
        );

        // Then: averageRating cursor 형식으로 응답이 조립되어야 한다.
        assertThat(response.products()).hasSize(1);
        assertThat(response.nextCursor()).isNotNull();
        assertThat(response.nextCursor().averageRating()).isEqualByComparingTo("4.8");
        assertThat(response.nextCursor().ratingNumber()).isNull();
        assertThat(response.nextCursor().price()).isNull();
    }

    @Test
    @DisplayName("정렬 기준이 여러 개 들어오면 ratingNumber를 우선해야 한다")
    void should_prioritize_rating_number_when_multiple_sort_values_are_requested() {
        // Given: ratingNumber 기준으로 다음 cursor를 만들 수 있는 검색 결과다.
        when(productRepository.findKeywordProductSummariesOrderByRatingNumber(
                "wire",
                null,
                null,
                2
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.8"), 50, new BigDecimal("19.99"))
        ));

        // When: 다중 sort 문자열로 keyword 검색을 수행한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "wire",
                1,
                null,
                "ratingNumber,averageRating,price",
                "desc"
        );

        // Then: ratingNumber cursor 형식으로 응답이 조립되어야 한다.
        assertThat(response.products()).hasSize(1);
        assertThat(response.nextCursor()).isNotNull();
        assertThat(response.nextCursor().averageRating()).isNull();
        assertThat(response.nextCursor().ratingNumber()).isEqualTo(50);
        assertThat(response.nextCursor().price()).isNull();
    }

    @Test
    @DisplayName("keyword 앞뒤 공백은 제거한 뒤 검색해야 한다")
    void should_trim_keyword_before_search_when_keyword_has_surrounding_spaces() {
        // Given: trim 이후 keyword로만 조회되는 검색 결과를 준비한다.
        when(productRepository.findKeywordProductSummariesOrderByAverageRating(
                "wire",
                null,
                null,
                2
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.8"), 50, new BigDecimal("19.99"))
        ));

        // When: 앞뒤 공백이 포함된 keyword로 검색한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "  wire  ",
                1,
                null,
                "averageRating",
                "desc"
        );

        // Then: trim된 keyword 기준의 결과를 반환해야 한다.
        assertThat(response.products()).extracting(product -> product.title())
                .containsExactly("Silver Wire Basket");
    }

    @Test
    @DisplayName("blank keyword면 빈 응답을 반환해야 한다")
    void should_return_empty_response_when_keyword_is_blank() {
        // Given: 공백만 들어온 keyword 요청이다.

        // When: blank keyword로 검색한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "   ",
                20,
                null,
                null,
                null
        );

        // Then: 예외 없이 빈 배열 응답을 반환해야 한다.
        assertThat(response.products()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 응답을 반환해야 한다")
    void should_return_empty_response_when_keyword_result_does_not_exist() {
        // Given: keyword에 일치하는 검색 결과가 없다.
        when(productRepository.findKeywordProductSummariesOrderByAverageRating(
                "missing",
                null,
                null,
                21
        )).thenReturn(List.of());

        // When: 결과가 없는 keyword로 검색한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "missing",
                20,
                null,
                null,
                null
        );

        // Then: 빈 배열 응답을 반환해야 한다.
        assertThat(response.products()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("cursorId가 존재하지 않으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_id_does_not_exist() {
        // Given: 존재하지 않는 cursor id다.
        when(productRepository.findDetailById(999L)).thenReturn(Optional.empty());

        // When: 존재하지 않는 cursorId로 검색한다.
        // Then: 잘못된 cursor로 400 예외를 던져야 한다.
        assertThatThrownBy(() -> productReadService.getProductsByKeyword(
                "wire",
                20,
                999L,
                "averageRating",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("cursor 상품이 keyword 검색 결과 집합에 속하지 않으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_product_does_not_match_keyword() {
        // Given: cursor 상품 title이 요청 keyword와 일치하지 않는다.
        when(productRepository.findDetailById(101L))
                .thenReturn(Optional.of(cursorProjection(
                        101L,
                        "Desk Lamp",
                        new BigDecimal("4.8"),
                        50,
                        new BigDecimal("19.99")
                )));

        // When: 다른 검색 집합의 cursor로 keyword 검색을 시도한다.
        // Then: 400 예외를 던져야 한다.
        assertThatThrownBy(() -> productReadService.getProductsByKeyword(
                "wire",
                20,
                101L,
                "averageRating",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("price 정렬에서 cursor 상품 price가 null이면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_product_price_is_null_for_price_sort() {
        // Given: cursor 상품은 존재하지만 price 값이 없다.
        when(productRepository.findDetailById(101L))
                .thenReturn(Optional.of(cursorProjection(
                        101L,
                        "Silver Wire Basket",
                        new BigDecimal("4.8"),
                        50,
                        null
                )));

        // When: price 정렬로 keyword 검색을 시도한다.
        // Then: 정렬 cursor 값 누락으로 400 예외를 던져야 한다.
        assertThatThrownBy(() -> productReadService.getProductsByKeyword(
                "wire",
                20,
                101L,
                "price",
                "asc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("averageRating 정렬에서 cursor 상품 averageRating이 null이면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_product_average_rating_is_null_for_average_rating_sort() {
        // Given: cursor 상품은 존재하지만 averageRating 값이 없다.
        when(productRepository.findDetailById(101L))
                .thenReturn(Optional.of(cursorProjection(
                        101L,
                        "Silver Wire Basket",
                        null,
                        50,
                        new BigDecimal("19.99")
                )));

        // When: averageRating 정렬로 keyword 검색을 시도한다.
        // Then: 정렬 cursor 값 누락으로 400 예외를 던져야 한다.
        assertThatThrownBy(() -> productReadService.getProductsByKeyword(
                "wire",
                20,
                101L,
                "averageRating",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("ratingNumber 정렬에서 cursor 상품 ratingNumber가 null이면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_product_rating_number_is_null_for_rating_number_sort() {
        // Given: cursor 상품은 존재하지만 ratingNumber 값이 없다.
        when(productRepository.findDetailById(101L))
                .thenReturn(Optional.of(cursorProjection(
                        101L,
                        "Silver Wire Basket",
                        new BigDecimal("4.8"),
                        null,
                        new BigDecimal("19.99")
                )));

        // When: ratingNumber 정렬로 keyword 검색을 시도한다.
        // Then: 정렬 cursor 값 누락으로 400 예외를 던져야 한다.
        assertThatThrownBy(() -> productReadService.getProductsByKeyword(
                "wire",
                20,
                101L,
                "ratingNumber",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("averageRating 정렬에서 다음 페이지가 있으면 averageRating cursor를 반환해야 한다")
    void should_return_next_cursor_when_average_rating_sort_has_next() {
        // Given: size + 1개의 averageRating 검색 결과를 준비한다.
        when(productRepository.findKeywordProductSummariesOrderByAverageRating(
                "wire",
                null,
                null,
                3
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.9"), 80, new BigDecimal("9.99")),
                projection(102L, "WIRE-002", "Travel Wire Pouch", new BigDecimal("4.7"), 60, new BigDecimal("12.99")),
                projection(103L, "WIRE-003", "Wire Organizer Tray", new BigDecimal("4.5"), 40, new BigDecimal("14.99"))
        ));

        // When: averageRating 기준 keyword 검색을 수행한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "wire",
                2,
                null,
                "averageRating",
                "desc"
        );

        // Then: 최대 size만 반환하고 마지막 응답 상품 기준 cursor를 만든다.
        assertThat(response.products()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor().id()).isEqualTo(102L);
        assertThat(response.nextCursor().averageRating()).isEqualByComparingTo("4.7");
    }

    @Test
    @DisplayName("ratingNumber 정렬에서 다음 페이지가 있으면 ratingNumber cursor를 반환해야 한다")
    void should_return_next_cursor_when_rating_number_sort_has_next() {
        // Given: size + 1개의 ratingNumber 검색 결과를 준비한다.
        when(productRepository.findKeywordProductSummariesOrderByRatingNumber(
                "wire",
                null,
                null,
                3
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.9"), 80, new BigDecimal("9.99")),
                projection(102L, "WIRE-002", "Travel Wire Pouch", new BigDecimal("4.7"), 60, new BigDecimal("12.99")),
                projection(103L, "WIRE-003", "Wire Organizer Tray", new BigDecimal("4.5"), 40, new BigDecimal("14.99"))
        ));

        // When: ratingNumber 기준 keyword 검색을 수행한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "wire",
                2,
                null,
                "ratingNumber",
                "desc"
        );

        // Then: 최대 size만 반환하고 마지막 응답 상품 기준 cursor를 만든다.
        assertThat(response.products()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor().id()).isEqualTo(102L);
        assertThat(response.nextCursor().ratingNumber()).isEqualTo(60);
    }

    @Test
    @DisplayName("price 정렬에서 다음 페이지가 있으면 price cursor를 반환해야 한다")
    void should_return_next_cursor_when_price_sort_has_next() {
        // Given: size + 1개의 price 검색 결과를 준비한다.
        when(productRepository.findKeywordProductSummariesOrderByPriceAsc(
                "wire",
                null,
                null,
                3
        )).thenReturn(List.of(
                projection(101L, "WIRE-001", "Silver Wire Basket", new BigDecimal("4.9"), 80, new BigDecimal("9.99")),
                projection(102L, "WIRE-002", "Travel Wire Pouch", new BigDecimal("4.7"), 60, new BigDecimal("12.99")),
                projection(103L, "WIRE-003", "Wire Organizer Tray", new BigDecimal("4.5"), 40, new BigDecimal("14.99"))
        ));

        // When: price ASC 기준 keyword 검색을 수행한다.
        ProductCategoryCursorResponse response = productReadService.getProductsByKeyword(
                "wire",
                2,
                null,
                "price",
                "asc"
        );

        // Then: 최대 size만 반환하고 마지막 응답 상품 기준 cursor를 만든다.
        assertThat(response.products()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor().id()).isEqualTo(102L);
        assertThat(response.nextCursor().price()).isEqualByComparingTo("12.99");
    }

    private ProductSummaryNativeProjection projection(
            Long id,
            String parentAsin,
            String title,
            BigDecimal averageRating,
            Integer ratingNumber,
            BigDecimal price
    ) {
        return new ProductSummaryNativeProjection() {
            @Override
            public Long getId() { return id; }

            @Override
            public String getParentAsin() { return parentAsin; }

            @Override
            public String getTitle() { return title; }

            @Override
            public String getMainCategory() { return "HANDMADE"; }

            @Override
            public BigDecimal getAverageRating() { return averageRating; }

            @Override
            public Integer getRatingNumber() { return ratingNumber; }

            @Override
            public BigDecimal getPrice() { return price; }

            @Override
            public String getStore() { return "Fixture Store"; }

            @Override
            public String getImageVariant() { return "MAIN"; }

            @Override
            public String getImageThumb() { return "thumb"; }

            @Override
            public String getImageLarge() { return "large"; }

            @Override
            public String getImageHiRes() { return "hires"; }
        };
    }

    private ProductDetailProjection cursorProjection(
            Long id,
            String title,
            BigDecimal averageRating,
            Integer ratingNumber,
            BigDecimal price
    ) {
        return new ProductDetailProjection() {
            @Override
            public Long getId() { return id; }

            @Override
            public String getParentAsin() { return "CURSOR-ASIN"; }

            @Override
            public String getTitle() { return title; }

            @Override
            public String getMainCategory() { return "HANDMADE"; }

            @Override
            public BigDecimal getAverageRating() { return averageRating; }

            @Override
            public Integer getRatingNumber() { return ratingNumber; }

            @Override
            public BigDecimal getPrice() { return price; }

            @Override
            public String getStore() { return "Fixture Store"; }

            @Override
            public String getDetails() { return "{}"; }
        };
    }
}
