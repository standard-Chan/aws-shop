package jeong.awsshop.product.service.productread.getproductsbycategory;

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
class ProductReadServiceGetProductsByCategoryTest {

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
                null,
                null,
                true,
                false
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
                null,
                null,
                false,
                true
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
    @DisplayName("두 정렬 옵션이 모두 true이면 averageRating을 우선해야 한다")
    void should_prioritize_average_rating_when_both_sort_options_are_true() {
        // Given: 두 정렬 옵션이 모두 true인 요청
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
                null,
                true,
                true
        );

        // Then: averageRating repository 메서드가 우선 호출되어야 한다
        verify(productRepository).findCategoryProductSummariesOrderByAverageRating(
                "HANDMADE",
                null,
                null,
                2
        );
    }

    @Test
    @DisplayName("정렬 옵션이 없으면 averageRating을 기본 정렬로 사용해야 한다")
    void should_use_average_rating_sort_when_sort_options_are_absent() {
        // Given: 정렬 옵션이 모두 false인 요청
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
                null,
                false,
                false
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
                null,
                null,
                true,
                false
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
    @DisplayName("cursorId만 있고 averageRating cursor 값이 없으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_id_exists_without_sort_cursor_value() {
        // Given: averageRating 정렬에서 cursorId만 있는 잘못된 요청

        // When & Then: cursor 값 조합 오류를 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                101L,
                null,
                null,
                true,
                false
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("정렬 cursor 값만 있고 cursorId가 없으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_sort_cursor_value_exists_without_cursor_id() {
        // Given: cursorAverageRating만 있는 잘못된 요청

        // When & Then: cursor 값 조합 오류를 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                null,
                new BigDecimal("4.5"),
                null,
                true,
                false
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("cursorId가 존재하지 않으면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_id_does_not_exist() {
        // Given: 존재하지 않는 cursor id
        Long cursorId = 9_999_999_999_999L;
        when(productRepository.existsById(cursorId)).thenReturn(false);

        // When & Then: 존재하지 않는 cursor는 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                new BigDecimal("4.5"),
                null,
                true,
                false
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("cursorId의 category가 요청 category와 다르면 400 예외를 던져야 한다")
    void should_throw_bad_request_when_cursor_category_is_different() {
        // Given: cursor id는 존재하지만 요청 category와 일치하지 않는다
        Long cursorId = 101L;
        when(productRepository.existsById(cursorId)).thenReturn(true);
        when(productRepository.existsByIdAndMainCategory(cursorId, "HANDMADE"))
                .thenReturn(false);

        // When & Then: 다른 category cursor는 400 예외로 처리해야 한다
        assertThatThrownBy(() -> productReadService.getProductsByCategory(
                "HANDMADE",
                1,
                cursorId,
                new BigDecimal("4.5"),
                null,
                true,
                false
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
                null,
                null,
                true,
                false
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
                null,
                null,
                false,
                true
        );

        // Then: 응답 마지막 상품 기준으로 ratingNumber cursor를 만들어야 한다
        CategoryCursor nextCursor = response.nextCursor();
        assertThat(response.hasNext()).isTrue();
        assertThat(nextCursor.id()).isEqualTo(102L);
        assertThat(nextCursor.averageRating()).isNull();
        assertThat(nextCursor.ratingNumber()).isEqualTo(12);
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
}
