package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import jeong.awsshop.product.domain.MainCategory;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.service.productread.dto.ProductCursorResponse;
import jeong.awsshop.product.service.productread.dto.ProductImageResponse;
import jeong.awsshop.product.service.productread.dto.ProductSummaryNativeProjection;
import jeong.awsshop.product.service.productread.dto.ProductSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductReadServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductReadService productReadService;

    @Test
    @DisplayName("상품 목록 조회 시 repository limit은 size + 1이어야 한다")
    void should_request_size_plus_one_when_get_products() {
        // Given: 첫 페이지 조회 조건과 빈 repository 응답
        when(productRepository.findProductSummaries(null, 4))
                .thenReturn(List.of());

        // When: size 3으로 목록을 조회한다
        productReadService.getProducts(3, null);

        // Then: hasNext 판단을 위해 size + 1을 조회해야 한다
        verify(productRepository).findProductSummaries(isNull(), eq(4));
    }

    @Test
    @DisplayName("cursor가 없으면 첫 페이지 조회를 repository에 전달해야 한다")
    void should_return_first_page_when_cursor_is_null() {
        // Given: cursor 없는 첫 페이지 조회 조건
        ProductSummaryNativeProjection first = projection(101L, "A-001", "Product A");
        ProductSummaryNativeProjection second = projection(102L, "A-002", "Product B");
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of(first, second));

        // When: cursor 없이 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: 첫 페이지 결과가 id 순서대로 반환되어야 한다
        assertThat(response.products()).extracting(ProductSummaryResponse::id)
                .containsExactly(101L, 102L);
        verify(productRepository).findProductSummaries(isNull(), eq(3));
    }

    @Test
    @DisplayName("cursor가 있으면 해당 cursor를 repository에 전달해야 한다")
    void should_return_next_page_when_cursor_exists() {
        // Given: snowflake cursor 이후 페이지 조회 조건
        Long cursor = 9_000_000_000_000L;
        ProductSummaryNativeProjection next = projection(9_000_000_000_100L, "A-003", "Product C");
        when(productRepository.findProductSummaries(cursor, 3))
                .thenReturn(List.of(next));

        // When: cursor를 포함해 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, cursor);

        // Then: cursor 이후 결과가 반환되어야 한다
        assertThat(response.products()).extracting(ProductSummaryResponse::id)
                .containsExactly(9_000_000_000_100L);
        verify(productRepository).findProductSummaries(cursor, 3);
    }

    @Test
    @DisplayName("조회 row가 size보다 많으면 hasNext는 true이고 응답은 size개만 포함해야 한다")
    void should_return_has_next_true_when_rows_are_more_than_size() {
        // Given: repository가 size + 1개를 반환한다
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of(
                        projection(101L, "A-001", "Product A"),
                        projection(102L, "A-002", "Product B"),
                        projection(103L, "A-003", "Product C")
                ));

        // When: size 2로 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: 응답은 size개만 담고 다음 페이지가 있다고 표시해야 한다
        assertThat(response.products()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("조회 row가 size 이하이면 hasNext는 false여야 한다")
    void should_return_has_next_false_when_rows_are_not_more_than_size() {
        // Given: repository가 size 이하로 반환한다
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of(
                        projection(101L, "A-001", "Product A"),
                        projection(102L, "A-002", "Product B")
                ));

        // When: size 2로 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: 다음 페이지가 없다고 표시해야 한다
        assertThat(response.products()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("응답 상품이 있으면 마지막 상품 id를 nextCursorId로 반환해야 한다")
    void should_return_last_product_id_as_next_cursor_id_when_products_exist() {
        // Given: 두 개의 응답 대상 상품
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of(
                        projection(101L, "A-001", "Product A"),
                        projection(102L, "A-002", "Product B")
                ));

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: 마지막 응답 상품 id가 nextCursorId가 되어야 한다
        assertThat(response.nextCursorId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("응답 상품이 없으면 nextCursorId는 null이어야 한다")
    void should_return_null_next_cursor_id_when_products_do_not_exist() {
        // Given: repository 조회 결과 없음
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of());

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: nextCursorId는 null이어야 한다
        assertThat(response.nextCursorId()).isNull();
    }

    @Test
    @DisplayName("repository 결과가 비어 있으면 빈 목록 응답을 반환해야 한다")
    void should_return_empty_products_when_repository_returns_empty_rows() {
        // Given: repository 조회 결과 없음
        when(productRepository.findProductSummaries(null, 3))
                .thenReturn(List.of());

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(2, null);

        // Then: 빈 products와 hasNext false를 반환해야 한다
        assertThat(response.products()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("native projection 필드를 상품 요약 응답으로 변환해야 한다")
    void should_map_native_projection_to_product_summary_response() {
        // Given: Product와 대표 image 필드를 가진 native projection
        ProductSummaryNativeProjection row = projection(101L, "A-001", "Product A");
        when(productRepository.findProductSummaries(null, 2))
                .thenReturn(List.of(row));

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(1, null);

        // Then: Product 필드가 응답 DTO에 매핑되어야 한다
        ProductSummaryResponse product = response.products().getFirst();
        assertThat(product.id()).isEqualTo(101L);
        assertThat(product.parentAsin()).isEqualTo("A-001");
        assertThat(product.title()).isEqualTo("Product A");
        assertThat(product.mainCategory()).isEqualTo(MainCategory.HANDMADE_PRODUCTS);
        assertThat(product.averageRating()).isEqualByComparingTo("4.5");
        assertThat(product.ratingNumber()).isEqualTo(12);
        assertThat(product.price()).isEqualByComparingTo("19.99");
        assertThat(product.store()).isEqualTo("Fixture Store");
    }

    @Test
    @DisplayName("대표 image projection 값이 있으면 image 응답을 생성해야 한다")
    void should_map_image_projection_to_image_response_when_image_exists() {
        // Given: 대표 image 값을 포함한 native projection
        when(productRepository.findProductSummaries(null, 2))
                .thenReturn(List.of(projection(101L, "A-001", "Product A")));

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(1, null);

        // Then: image 응답이 생성되어야 한다
        ProductImageResponse image = response.products().getFirst().image();
        assertThat(image.variant()).isEqualTo("MAIN");
        assertThat(image.thumb()).isEqualTo("main-thumb");
        assertThat(image.large()).isEqualTo("main-large");
        assertThat(image.hiRes()).isEqualTo("main-hires");
    }

    @Test
    @DisplayName("image projection 값이 모두 null이면 image는 null이어야 한다")
    void should_return_null_image_when_image_projection_is_null() {
        // Given: image 값이 없는 native projection
        ProductSummaryNativeProjection row = projectionWithoutImage(101L, "A-001", "Product A");
        when(productRepository.findProductSummaries(null, 2))
                .thenReturn(List.of(row));

        // When: 상품 목록을 조회한다
        ProductCursorResponse response = productReadService.getProducts(1, null);

        // Then: image는 null이어야 한다
        assertThat(response.products().getFirst().image()).isNull();
    }

    @Test
    @DisplayName("size가 0 이하이면 상품 목록 조회를 거절해야 한다")
    void should_throw_bad_request_when_size_is_not_positive() {
        // Given: 유효하지 않은 size 값

        // When & Then: size가 0이면 400 예외를 던져야 한다
        assertThatThrownBy(() -> productReadService.getProducts(0, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        // When & Then: size가 음수이면 400 예외를 던져야 한다
        assertThatThrownBy(() -> productReadService.getProducts(-1, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    @DisplayName("size가 100보다 크면 상품 목록 조회를 거절해야 한다")
    void should_throw_bad_request_when_size_is_greater_than_max() {
        // Given: 최대값을 초과한 size

        // When & Then: size 101은 400 예외를 던져야 한다
        assertThatThrownBy(() -> productReadService.getProducts(101, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private ProductSummaryNativeProjection projection(Long id, String parentAsin, String title) {
        return new ProductSummaryNativeProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return parentAsin; }
            public String getTitle() { return title; }
            public String getMainCategory() { return "HANDMADE_PRODUCTS"; }
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

    private ProductSummaryNativeProjection projectionWithoutImage(Long id, String parentAsin, String title) {
        return new ProductSummaryNativeProjection() {
            public Long getId() { return id; }
            public String getParentAsin() { return parentAsin; }
            public String getTitle() { return title; }
            public String getMainCategory() { return "HANDMADE_PRODUCTS"; }
            public BigDecimal getAverageRating() { return new BigDecimal("4.5"); }
            public Integer getRatingNumber() { return 12; }
            public BigDecimal getPrice() { return new BigDecimal("19.99"); }
            public String getStore() { return "Fixture Store"; }
            public String getImageVariant() { return null; }
            public String getImageThumb() { return null; }
            public String getImageLarge() { return null; }
            public String getImageHiRes() { return null; }
        };
    }
}
