package jeong.awsshop.eventpipeline.hadoopconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventHadoopConsumerApplication {

    // Kafka 이벤트를 Hadoop 적재용 staging 파일로 모으는 비웹 Spring Boot 애플리케이션 진입점이다.
    public static void main(String[] args) {
        SpringApplication.run(EventHadoopConsumerApplication.class, args);
    }
}
