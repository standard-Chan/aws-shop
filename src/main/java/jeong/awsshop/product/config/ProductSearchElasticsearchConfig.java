package jeong.awsshop.product.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProductSearchElasticsearchProperties.class)
@ConditionalOnProperty(prefix = "app.product-search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchElasticsearchConfig {

    @Bean
    public RestClient productSearchRestClient(ProductSearchElasticsearchProperties properties) {
        List<HttpHost> hosts = properties.uris().stream()
                .map(HttpHost::create)
                .toList();
        return RestClient.builder(hosts.toArray(HttpHost[]::new)).build();
    }

    @Bean
    public ElasticsearchTransport productSearchElasticsearchTransport(
            RestClient productSearchRestClient,
            ObjectMapper objectMapper
    ) {
        return new RestClientTransport(productSearchRestClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient productSearchElasticsearchClient(
            ElasticsearchTransport productSearchElasticsearchTransport
    ) {
        return new ElasticsearchClient(productSearchElasticsearchTransport);
    }
}
