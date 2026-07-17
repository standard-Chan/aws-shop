package jeong.awsshop.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jeong.awsshop.product.service.productread.dto.ProductDetailResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ProductDetailCacheProperties.class)
public class ProductDetailCacheConfig {

    @Bean
    public RedisTemplate<String, ProductDetailResponse> productDetailRedisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper
    ) {
        RedisTemplate<String, ProductDetailResponse> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(productDetailResponseSerializer(objectMapper));
        redisTemplate.setHashValueSerializer(productDetailResponseSerializer(objectMapper));
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public TaskExecutor productDetailCacheTaskExecutor(ProductDetailCacheProperties properties) {
        ProductDetailCacheProperties.Async async = properties.async();

        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(async.corePoolSize());
        taskExecutor.setMaxPoolSize(async.maxPoolSize());
        taskExecutor.setQueueCapacity(async.queueCapacity());
        taskExecutor.setThreadNamePrefix("product-detail-cache-");
        taskExecutor.initialize();
        return taskExecutor;
    }

    private Jackson2JsonRedisSerializer<ProductDetailResponse> productDetailResponseSerializer(
            ObjectMapper objectMapper
    ) {
        return new Jackson2JsonRedisSerializer<>(objectMapper, ProductDetailResponse.class);
    }
}
