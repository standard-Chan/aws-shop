package jeong.awsshop.product.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.product-search.elasticsearch")
public record ProductSearchElasticsearchProperties(
        boolean enabled,
        List<String> uris,
        String indexName
) {
}
