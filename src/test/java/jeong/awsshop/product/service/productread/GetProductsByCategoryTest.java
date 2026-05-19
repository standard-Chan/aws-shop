package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.ProductReadService;
import jeong.awsshop.product.service.productread.dto.CategoryCursor;
import jeong.awsshop.product.service.productread.dto.ProductCategoryCursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GetProductsByCategoryTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductReadService productReadService;

    @Test
    @DisplayName("averageRating 정렬 요청이면 averageRating repository 메서드를 호출해야 한다")
    void should_call_average_rating_repository_when_average_rating_sort_is_requested() {
        // Given: averageRating 정렬 첫 페이지 요청과 repository 응답
        when(productRepository.findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(projection(101L, "A-001", "Product A")));

        // When: category 상품을 averageRating 기준으로 조회한다
        ProductCategoryCursorResponse response = productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "averageRating",
                "desc"
        );

        // Then: averageRating repository 메서드와 응답 DTO가 사용되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                3
        );
        assertThat(response.products()).hasSize(1);
    }

    @Test
    @DisplayName("ratingNumber 정렬 요청이면 ratingNumber repository 메서드를 호출해야 한다")
    void should_call_rating_number_repository_when_rating_number_sort_is_requested() {
        // Given: ratingNumber 정렬 첫 페이지 요청과 repository 응답
        when(productRepository.findCategoryProductSummariesOrderByRatingNumber(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(projection(101L, "A-001", "Product A")));

        // When: category 상품을 ratingNumber 기준으로 조회한다
        ProductCategoryCursorResponse response = productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "ratingNumber",
                "desc"
        );

        // Then: ratingNumber repository 메서드와 응답 DTO가 사용되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByRatingNumber(
                "HANDMADE",
                null,
                null,
                3
        );
        assertThat(response.products()).hasSize(1);
    }

    @Test
    @DisplayName("price ASC 정렬 요청이면 price ASC repository 메서드를 호출해야 한다")
    void should_call_price_asc_repository_when_price_sort_and_asc_direction_are_requested() {
        // Given: price ASC 첫 페이지 요청과 repository 응답
        when(productRepository.findCategoryProductSummariesOrderByPriceAsc(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(projection(101L, "A-001", "Product A")));

        // When: category 상품을 price ASC 기준으로 조회한다
        productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "price",
                "asc"
        );

        // Then: price ASC repository 메서드가 호출되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByPriceAsc(
                "HANDMADE",
                null,
                null,
                3
        );
    }

    @Test
    @DisplayName("price DESC 정렬 요청이면 price DESC repository 메서드를 호출해야 한다")
    void should_call_price_desc_repository_when_price_sort_and_desc_direction_are_requested() {
        // Given: price DESC 첫 페이지 요청과 repository 응답
        when(productRepository.findCategoryProductSummariesOrderByPriceDesc(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(projection(101L, "A-001", "Product A")));

        // When: category 상품을 price DESC 기준으로 조회한다
        productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "price",
                "desc"
        );

        // Then: price DESC repository 메서드가 호출되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByPriceDesc(
                "HANDMADE",
                null,
                null,
                3
        );
    }

    @Test
    @DisplayName("정렬 기준이 여러 개 들어오면 ratingNumber를 우선해야 한다")
    void should_prioritize_rating_number_when_multiple_sort_values_are_requested() {
        // Given: ratingNumber, averageRating, price가 함께 들어온 요청
        when(productRepository.findCategoryProductSummariesOrderByRatingNumber(
                "HANDMADE",
                null,
                null,
                2
        )).thenReturn(List.of());

        // When: category 상품을 다중 sort 기준으로 조회한다
        productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                null,
                "ratingNumber,averageRating,price",
                "desc"
        );

        // Then: 우선순위가 가장 높은 ratingNumber repository만 호출되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByRatingNumber(
                "HANDMADE",
                null,
                null,
                2
        );
    }

    @Test
    @DisplayName("sort가 없으면 averageRating을 기본 정렬로 사용해야 한다")
    void should_use_average_rating_sort_when_sort_is_absent() {
        // Given: sort 없이 조회하는 요청
        when(productRepository.findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                2
        )).thenReturn(List.of());

        // When: category 상품을 조회한다
        productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                null,
                null,
                null
        );

        // Then: averageRating repository 메서드가 기본으로 호출되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                2
        );
    }

    @Test
    @DisplayName("예상하지 못한 category도 정규화해서 조회에 사용해야 한다")
    void should_normalize_unknown_category_and_use_it_for_query() {
        // Given: 사전에 정의되지 않은 category 문자열
        when(productRepository.findCategoryProductSummariesOrderByAverageRating(
                "NOT_A_CATEGORY",
                null,
                null,
                2
        )).thenReturn(List.of());

        // When: 하이픈이 포함된 category로 조회한다
        productReadService.getProductsByCategory(
                "Not-A-Category",
                1,
                null,
                "averageRating",
                "desc"
        );

        // Then: service는 category를 정규화한 뒤 repository에 전달해야 한다
        verify(productRepository).findCategoryProductSummariesOrderByAverageRating(
                "NOT_A_CATEGORY",
                null,
                null,
                2
        );
    }

    @Test
    @DisplayName("price 정렬에서 cursorId가 있으면 cursor 상품의 price를 조회해 repository에 전달해야 한다")
    void should_pass_cursor_product_price_to_price_asc_repository_when_cursor_id_exists() {
        // Given: price ASC cursor 요청과 cursor 상품 조회 결과
        Long cursorId = 101L;
        BigDecimal cursorPrice = new BigDecimal("19.99");
        when(productRepository.findDetailById(cursorId))
                .thenReturn(java.util.Optional.of(cursorProjection(cursorId, "HANDMADE", null, null, cursorPrice)));
        when(productRepository.findCategoryProductSummariesOrderByPriceAsc(
                "HANDMADE",
                cursorId,
                cursorPrice,
                2
        )).thenReturn(List.of());

        // When: category 상품을 price ASC cursor 기준으로 조회한다
        productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                "price",
                "asc"
        );

        // Then: repository는 price cursor 값을 그대로 받아야 한다
        verify(productRepository).findCategoryProductSummariesOrderByPriceAsc(
                "HANDMADE",
                cursorId,
                cursorPrice,
                2
        );
    }

    @Test
    @DisplayName("price 정렬에서 cursor 상품의 price가 null이면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_product_price_is_null_for_price_sort() {
        // Given: cursor 상품은 존재하지만 price 값이 없다
        Long cursorId = 101L;
        when(productRepository.findDetailById(cursorId))
                .thenReturn(java.util.Optional.of(cursorProjection(cursorId, "HANDMADE", null, null, null)));

        // When & Then: price 정렬에 필요한 cursor 값이 없으므로 400 예외를 던져야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                "price",
                "asc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("cursorId가 존재하지 않으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_id_does_not_exist() {
        // Given: 존재하지 않는 cursor id
        Long cursorId = 9_999_999_999_999L;
        when(productRepository.findDetailById(cursorId)).thenReturn(java.util.Optional.empty());

        // When & Then: 존재하지 않는 cursor는 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                "averageRating",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("cursorId의 category가 요청 category와 다르면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_category_is_different() {
        // Given: cursor id는 존재하지만 요청 category와 일치하지 않는다
        Long cursorId = 101L;
        when(productRepository.findDetailById(cursorId))
                .thenReturn(java.util.Optional.of(cursorProjection(cursorId, "GIFT_CARDS", new BigDecimal("4.5"), 12, new BigDecimal("19.99"))));

        // When & Then: 다른 category cursor는 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                "averageRating",
                "desc"
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("averageRating 정렬 응답이면 nextCursor에 id와 averageRating만 포함해야 한다")
    void should_return_average_rating_cursor_when_average_rating_sort_has_next() {
        // Given: size + 1개 row로 다음 페이지가 있는 상황
        when(productRepository.findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(
                projection(101L, "A-001", "Product A"),
                projection(102L, "A-002", "Product B"),
                projection(103L, "A-003", "Product C")
        ));

        // When: averageRating 기준 category 상품을 조회한다
        ProductCategoryCursorResponse response = productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "averageRating",
                "desc"
        );

        // Then: 응답 마지막 상품 기준으로 averageRating cursor를 만들어야 한다
        CategoryCursor nextCursor = response.nextCursor();
        assertThat(response.hasNext()).isTrue();
        assertThat(nextCursor.id()).isEqualTo(102L);
        assertThat(nextCursor.averageRating()).isEqualByComparingTo("4.5");
        assertThat(nextCursor.ratingNumber()).isNull();
    }

    @Test
    @DisplayName("ratingNumber 정렬 응답이면 nextCursor에 id와 ratingNumber만 포함해야 한다")
    void should_return_rating_number_cursor_when_rating_number_sort_has_next() {
        // Given: size + 1개 row로 다음 페이지가 있는 상황
        when(productRepository.findCategoryProductSummariesOrderByRatingNumber(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(
                projection(101L, "A-001", "Product A"),
                projection(102L, "A-002", "Product B"),
                projection(103L, "A-003", "Product C")
        ));

        // When: ratingNumber 기준 category 상품을 조회한다
        ProductCategoryCursorResponse response = productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "ratingNumber",
                "desc"
        );

        // Then: 응답 마지막 상품 기준으로 ratingNumber cursor를 만들어야 한다
        CategoryCursor nextCursor = response.nextCursor();
        assertThat(response.hasNext()).isTrue();
        assertThat(nextCursor.id()).isEqualTo(102L);
        assertThat(nextCursor.averageRating()).isNull();
        assertThat(nextCursor.ratingNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("price 정렬 응답이면 nextCursor에 id와 price만 포함해야 한다")
    void should_return_price_cursor_when_price_sort_has_next() {
        // Given: size + 1개 row로 다음 페이지가 있는 상황
        when(productRepository.findCategoryProductSummariesOrderByPriceAsc(
                "HANDMADE",
                null,
                null,
                3
        )).thenReturn(List.of(
                projectionWithPrice(101L, "A-001", "Product A", "10.00"),
                projectionWithPrice(102L, "A-002", "Product B", "19.99"),
                projectionWithPrice(103L, "A-003", "Product C", "30.00")
        ));

        // When: price 기준 category 상품을 조회한다
        ProductCategoryCursorResponse response = productReadService.getProductsByCategory(
                "HANDMADE",
                2,
                null,
                "price",
                "asc"
        );

        // Then: 응답 마지막 상품 기준으로 price cursor를 만들어야 한다
        CategoryCursor nextCursor = response.nextCursor();
        assertThat(response.hasNext()).isTrue();
        assertThat(nextCursor.id()).isEqualTo(102L);
        assertThat(nextCursor.averageRating()).isNull();
        assertThat(nextCursor.ratingNumber()).isNull();
        assertThat(nextCursor.price()).isEqualByComparingTo("19.99");
    }

    private ProductSummaryNativeProjection projection(Long id, String parentAsin, String title) {
        return new ProductSummaryNativeProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return parentAsin; }
            public String getTitle() { return title; }
            public String getMainCategory() { return "HANDMADE"; }
            public BigDecimal getAverageRating() { return new BigDecimal("4.5"); }
            public Integer getRatingNumber() { return 12; }
            public BigDecimal getPrice() { return new BigDecimal("19.99"); }
            public String getStore() { return "Fixture Store"; }
            public String getImageVariant() { return "MAIN"; }
            public String getImageThumb() { return "main-thumb"; }
            public String getImageLarge() { return "main-large"; }
            public String getImageHiRes() { return "main-hires"; }
        };
    }

    private ProductSummaryNativeProjection projectionWithPrice(Long id, String parentAsin, String title, String price) {
        return new ProductSummaryNativeProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return parentAsin; }
            public String getTitle() { return title; }
            public String getMainCategory() { return "HANDMADE"; }
            public BigDecimal getAverageRating() { return new BigDecimal("4.5"); }
            public Integer getRatingNumber() { return 12; }
            public BigDecimal getPrice() { return new BigDecimal(price); }
            public String getStore() { return "Fixture Store"; }
            public String getImageVariant() { return "MAIN"; }
            public String getImageThumb() { return "main-thumb"; }
            public String getImageLarge() { return "main-large"; }
            public String getImageHiRes() { return "main-hires"; }
        };
    }

    private jeong.awsshop.product.repository.projection.ProductDetailProjection cursorProjection(
            Long id,
            String mainCategory,
            BigDecimal averageRating,
            Integer ratingNumber,
            BigDecimal price
    ) {
        return new jeong.awsshop.product.repository.projection.ProductDetailProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return "CURSOR"; }
            public String getTitle() { return "Cursor Product"; }
            public String getMainCategory() { return mainCategory; }
            public BigDecimal getAverageRating() { return averageRating; }
            public Integer getRatingNumber() { return ratingNumber; }
            public BigDecimal getPrice() { return price; }
            public String getStore() { return "Cursor Store"; }
            public String getDetails() { return "{}"; }
        };
    }
}
