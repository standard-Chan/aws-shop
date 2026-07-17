package jeong.awsshop.product.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.product.detail-cache")
public record ProductDetailCacheProperties(
        boolean enabled,
        Duration ttl,
        Async async
) {

    public ProductDetailCacheProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (async == null) {
            async = new Async(2, 8, 1000);
        }
    }

    public record Async(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {
    }
}
