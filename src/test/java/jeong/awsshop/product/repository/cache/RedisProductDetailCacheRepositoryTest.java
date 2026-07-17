package jeong.awsshop.product.repository.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jeong.awsshop.product.config.ProductDetailCacheProperties;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisProductDetailCacheRepositoryTest {

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    @Mock
    private RedisTemplate<String, ProductDetailResponse> redisTemplate;

    @Mock
    private ValueOperations<String, ProductDetailResponse> valueOperations;

    @Test
    @DisplayName("상품 상세 캐시 조회 시 값을 읽고 TTL을 갱신해야 한다")
    void should_get_product_detail_and_refresh_ttl_when_cache_exists() {
        // Given: Redis에 상품 상세 응답이 저장되어 있다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse cachedResponse = detailResponse(productId);
        RedisProductDetailCacheRepository repository = repository();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndExpire(cacheKey(productId), CACHE_TTL))
                .thenReturn(cachedResponse);

        // When: 상품 상세 캐시를 조회한다
        Optional<ProductDetailResponse> response = repository.findByProductId(productId);

        // Then: 캐시 값을 반환하고 TTL을 갱신해야 한다
        assertThat(response).contains(cachedResponse);
        verify(valueOperations).getAndExpire(cacheKey(productId), CACHE_TTL);
    }

    @Test
    @DisplayName("상품 상세 캐시가 없으면 Optional.empty를 반환해야 한다")
    void should_return_empty_when_cache_does_not_exist() {
        // Given: Redis에 상품 상세 응답이 없다
        Long productId = 9_000_000_000_000L;
        RedisProductDetailCacheRepository repository = repository();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndExpire(cacheKey(productId), CACHE_TTL))
                .thenReturn(null);

        // When: 상품 상세 캐시를 조회한다
        Optional<ProductDetailResponse> response = repository.findByProductId(productId);

        // Then: 빈 Optional을 반환해야 한다
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("상품 상세 캐시 조회 중 Redis 장애가 발생하면 Optional.empty를 반환해야 한다")
    void should_return_empty_when_redis_get_fails() {
        // Given: Redis 조회가 실패한다
        Long productId = 9_000_000_000_000L;
        RedisProductDetailCacheRepository repository = repository();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndExpire(cacheKey(productId), CACHE_TTL))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // When: 상품 상세 캐시를 조회한다
        Optional<ProductDetailResponse> response = repository.findByProductId(productId);

        // Then: 예외를 전파하지 않고 MISS처럼 처리해야 한다
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("상품 상세 캐시 저장 시 TTL과 함께 저장해야 한다")
    void should_save_product_detail_with_ttl() {
        // Given: 저장할 상품 상세 응답이 있다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse response = detailResponse(productId);
        RedisProductDetailCacheRepository repository = repository();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When: 상품 상세 캐시에 저장한다
        repository.save(productId, response);

        // Then: TTL과 함께 저장해야 한다
        verify(valueOperations).set(cacheKey(productId), response, CACHE_TTL);
    }

    @Test
    @DisplayName("상품 상세 캐시 저장 중 Redis 장애가 발생해도 예외를 전파하지 않아야 한다")
    void should_not_throw_when_redis_save_fails() {
        // Given: Redis 저장이 실패한다
        Long productId = 9_000_000_000_000L;
        ProductDetailResponse response = detailResponse(productId);
        RedisProductDetailCacheRepository repository = repository();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(cacheKey(productId), response, CACHE_TTL);

        // When & Then: 저장 실패는 API 실패로 전파하지 않아야 한다
        assertThatCode(() -> repository.save(productId, response))
                .doesNotThrowAnyException();
    }

    private RedisProductDetailCacheRepository repository() {
        return new RedisProductDetailCacheRepository(
                redisTemplate,
                new ProductDetailCacheProperties(true, CACHE_TTL)
        );
    }

    private String cacheKey(Long productId) {
        return "product:detail:" + productId;
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
