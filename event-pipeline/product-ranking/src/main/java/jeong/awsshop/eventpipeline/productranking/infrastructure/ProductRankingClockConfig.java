package jeong.awsshop.eventpipeline.productranking.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductRankingClockConfig {

    @Bean
    public Clock productRankingClock() {
        return Clock.systemUTC();
    }
}
