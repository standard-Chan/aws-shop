package jeong.awsshop.product.service.productread;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jeong.awsshop.product.repository.cache.ProductDetailCacheRepository;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductDetailCacheAsyncWriterTest {

    @Mock
    private ProductDetailCacheRepository productDetailCacheRepository;

    @Test
    @DisplayName("상품 상세 캐시 비동기 writer는 cache repository 저장을 호출해야 한다")
    void should_save_product_detail_cache() {
        // Given: 저장할 상품 상세 응답이 있다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse response = detailResponse(productId);
        ProductDetailCacheAsyncWriter writer = new ProductDetailCacheAsyncWriter(productDetailCacheRepository);

        // When: 캐시 저장을 요청한다
        writer.saveAsync(productId, response);

        // Then: cache repository 저장을 호출해야 한다
        verify(productDetailCacheRepository).save(productId, response);
    }

    @Test
    @DisplayName("상품 상세 캐시 비동기 저장 실패는 writer 밖으로 전파하지 않아야 한다")
    void should_not_throw_when_cache_save_fails() {
        // Given: cache repository 저장이 실패한다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse response = detailResponse(productId);
        ProductDetailCacheAsyncWriter writer = new ProductDetailCacheAsyncWriter(productDetailCacheRepository);
        doThrow(new IllegalStateException("cache save failed"))
                .when(productDetailCacheRepository)
                .save(productId, response);

        // When & Then: 저장 실패를 writer 내부에서 처리해야 한다
        assertThatCode(() -> writer.saveAsync(productId, response))
                .doesNotThrowAnyException();
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
