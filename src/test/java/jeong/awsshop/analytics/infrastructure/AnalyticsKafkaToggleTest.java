package jeong.awsshop.analytics.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jeong.awsshop.analytics.application.AnalyticsEventPublisher;
import jeong.awsshop.analytics.application.AnalyticsEventStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class AnalyticsKafkaToggleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            KafkaAnalyticsEventPublisher.class,
            KafkaAnalyticsEventConsumer.class,
            DisabledAnalyticsEventPublisher.class
        )
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withBean(AnalyticsEventStoreService.class, () -> mock(AnalyticsEventStoreService.class));

    @Test
    void should_enable_kafka_publisher_and_consumer_by_default() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AnalyticsEventPublisher.class);
            assertThat(context).hasSingleBean(KafkaAnalyticsEventPublisher.class);
            assertThat(context).hasSingleBean(KafkaAnalyticsEventConsumer.class);
            assertThat(context).doesNotHaveBean(DisabledAnalyticsEventPublisher.class);
        });
    }

    @Test
    void should_use_disabled_publisher_without_consumer_when_kafka_is_disabled() {
        contextRunner
            .withPropertyValues("app.analytics.kafka.enabled=false")
            .run(context -> {
                assertThat(context).hasSingleBean(AnalyticsEventPublisher.class);
                assertThat(context).hasSingleBean(DisabledAnalyticsEventPublisher.class);
                assertThat(context).doesNotHaveBean(KafkaAnalyticsEventPublisher.class);
                assertThat(context).doesNotHaveBean(KafkaAnalyticsEventConsumer.class);
            });
    }
}
