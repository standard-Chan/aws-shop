package jeong.awsshop.product.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.product.detail-cache")
public record ProductDetailCacheProperties(
        boolean enabled,
        Duration ttl
) {
}
