package jeong.awsshop.eventpipeline.producer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventTopicProperties.class)
public class EventProducerConfig {
}
