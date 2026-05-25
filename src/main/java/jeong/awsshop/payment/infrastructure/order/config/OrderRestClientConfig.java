package jeong.awsshop.payment.infrastructure.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OrderRestClientConfig {

    @Bean
    public RestClient orderRestClient(
        @Value("${external-api.order-server.base-url}") String orderServerBaseUrl
    ) {
        return RestClient.builder()
            .baseUrl(orderServerBaseUrl)
            .build();
    }
}
