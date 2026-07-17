package jeong.awsshop.product.service.productread;

import jeong.awsshop.product.repository.cache.ProductDetailCacheRepository;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDetailCacheAsyncWriter {

    private final ProductDetailCacheRepository productDetailCacheRepository;

    @Async("productDetailCacheTaskExecutor")
    public void saveAsync(Long productId, ProductDetailResponse response) {
        try {
            productDetailCacheRepository.save(productId, response);
        } catch (RuntimeException e) {
            log.warn("[Product 상세 캐시 비동기 저장 실패]: 캐시 저장을 건너뜁니다. productId={}", productId, e);
        }
    }
}
