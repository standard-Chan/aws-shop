package jeong.awsshop.analytics.infrastructure;

import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class AnalyticsKafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AnalyticsEventMessage>
    analyticsKafkaBatchListenerContainerFactory(
            ConsumerFactory<String, AnalyticsEventMessage> consumerFactory,
            @Value("${app.analytics.kafka.consumer.concurrency:1}") Integer concurrency
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AnalyticsEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency);
        return factory;
    }
}
