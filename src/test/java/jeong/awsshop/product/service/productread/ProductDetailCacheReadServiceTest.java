package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jeong.awsshop.product.config.ProductDetailCacheProperties;
import jeong.awsshop.product.exception.productread.ProductNotFoundException;
import jeong.awsshop.product.repository.ProductRepository;
import jeong.awsshop.product.repository.cache.ProductDetailCacheRepository;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductDetailCacheReadServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDetailDbReader productDetailDbReader;

    @Mock
    private ProductDetailCacheRepository productDetailCacheRepository;

    @Mock
    private ProductDetailCacheAsyncWriter productDetailCacheAsyncWriter;

    @Test
    @DisplayName("상품 상세 캐시가 OFF이면 캐시를 조회하지 않고 DB에서 바로 조회해야 한다")
    void should_read_from_db_without_cache_lookup_when_product_detail_cache_is_disabled() {
        // Given: 상세 캐시가 비활성화되어 있고 DB reader가 응답을 반환한다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse dbResponse = detailResponse(productId);
        ProductReadService productReadService = productReadService(false);
        when(productDetailDbReader.readProductDetail(productId)).thenReturn(dbResponse);

        // When: 상품 상세를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: 캐시 조회/저장 없이 DB 응답을 반환해야 한다
        assertThat(response).isSameAs(dbResponse);
        verify(productDetailDbReader).readProductDetail(productId);
        verifyNoInteractions(productDetailCacheRepository);
        verifyNoInteractions(productDetailCacheAsyncWriter);
    }

    @Test
    @DisplayName("상품 상세 캐시 HIT이면 DB를 조회하지 않고 캐시 응답을 반환해야 한다")
    void should_return_cached_product_detail_without_db_lookup_when_cache_hits() {
        // Given: 상세 캐시가 활성화되어 있고 캐시에 응답이 있다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse cachedResponse = detailResponse(productId);
        ProductReadService productReadService = productReadService(true);
        when(productDetailCacheRepository.findByProductId(productId))
                .thenReturn(Optional.of(cachedResponse));

        // When: 상품 상세를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: DB reader를 호출하지 않고 캐시 응답을 반환해야 한다
        assertThat(response).isSameAs(cachedResponse);
        verify(productDetailCacheRepository).findByProductId(productId);
        verifyNoInteractions(productDetailDbReader);
        verifyNoInteractions(productDetailCacheAsyncWriter);
    }

    @Test
    @DisplayName("상품 상세 캐시 MISS이면 DB 조회 후 캐시 저장을 비동기로 요청해야 한다")
    void should_read_from_db_and_request_async_cache_save_when_cache_misses() {
        // Given: 상세 캐시가 활성화되어 있고 캐시가 비어 있다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse dbResponse = detailResponse(productId);
        ProductReadService productReadService = productReadService(true);
        when(productDetailCacheRepository.findByProductId(productId))
                .thenReturn(Optional.empty());
        when(productDetailDbReader.readProductDetail(productId)).thenReturn(dbResponse);

        // When: 상품 상세를 조회한다
        ProductDetailResponse response = productReadService.getProductDetail(productId);

        // Then: DB 응답을 반환하고 캐시 저장은 비동기로 요청해야 한다
        assertThat(response).isSameAs(dbResponse);
        verify(productDetailDbReader).readProductDetail(productId);
        verify(productDetailCacheAsyncWriter).saveAsync(productId, dbResponse);
        verify(productDetailCacheRepository, never()).save(productId, dbResponse);
    }

    @Test
    @DisplayName("상품 상세 DB 조회가 실패하면 404 결과를 캐싱하지 않아야 한다")
    void should_not_save_cache_when_product_detail_does_not_exist() {
        // Given: 상세 캐시가 활성화되어 있고 DB에 상품이 없다
        Long productId = 9_999_999_999_999L;
        ProductReadService productReadService = productReadService(true);
        when(productDetailCacheRepository.findByProductId(productId))
                .thenReturn(Optional.empty());
        when(productDetailDbReader.readProductDetail(productId))
                .thenThrow(new ProductNotFoundException(productId));

        // When & Then: 예외는 유지하고 캐시 저장은 요청하지 않아야 한다
        assertThatThrownBy(() -> productReadService.getProductDetail(productId))
                .isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(productDetailCacheAsyncWriter);
    }

    private ProductReadService productReadService(boolean cacheEnabled) {
        return new ProductReadService(
                productRepository,
                productDetailDbReader,
                productDetailCacheRepository,
                productDetailCacheAsyncWriter,
                new ProductDetailCacheProperties(
                        cacheEnabled,
                        Duration.ofHours(1),
                        new ProductDetailCacheProperties.Async(2, 8, 1000)
                )
        );
    }

    private ProductDetailResponse detailResponse(Long productId) {
        return new ProductDetailResponse(
                String.valueOf(productId),
                "B07NK78DVV",
                "Psychedelic Swirls Key Fob",
                "HANDMADE",
                new BigDecimal("4.9"),
                14,
                new BigDecimal("17.99"),
                "Green Acorn Kitchen",
                Map.of("Department", "unisex-adult"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
