package jeong.awsshop.eventpipeline.monolith.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventMonolithClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
