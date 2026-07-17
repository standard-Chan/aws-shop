package jeong.awsshop.product.repository.cache;

import java.time.Duration;
import java.util.Optional;
import jeong.awsshop.product.config.ProductDetailCacheProperties;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisProductDetailCacheRepository implements ProductDetailCacheRepository {

    private static final String KEY_PREFIX = "product:detail:";

    private final RedisTemplate<String, ProductDetailResponse> redisTemplate;
    private final ProductDetailCacheProperties properties;

    @Override
    public Optional<ProductDetailResponse> findByProductId(Long productId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue()
                    .getAndExpire(cacheKey(productId), cacheTtl()));
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[Product 상세 캐시 조회 실패]: Redis 장애로 DB 조회로 fallback합니다. productId={}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long productId, ProductDetailResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey(productId), response, cacheTtl());
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[Product 상세 캐시 저장 실패]: Redis 장애로 캐시 저장을 건너뜁니다. productId={}", productId, e);
        }
    }

    private Duration cacheTtl() {
        return properties.ttl();
    }

    private String cacheKey(Long productId) {
        return KEY_PREFIX + productId;
    }
}
