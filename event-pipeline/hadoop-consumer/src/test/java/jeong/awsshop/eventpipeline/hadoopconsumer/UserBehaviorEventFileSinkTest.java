package jeong.awsshop.eventpipeline.hadoopconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserBehaviorEventFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Instant occurredAt을 ISO-8601 JSONL로 저장해야 한다")
    void shouldWriteInstantOccurredAtAsIsoJsonLine() throws Exception {
        ObjectMapper objectMapper = new EventHadoopConsumerKafkaConfig().objectMapper();
        Path outputPath = tempDir.resolve("user-behavior-events.jsonl");
        UserBehaviorEventFileSink fileSink = new UserBehaviorEventFileSink(outputPath, objectMapper);

        fileSink.append(UserBehaviorEventMessage.newMessage(
                1L,
                UserBehaviorEventType.SEARCH,
                10L,
                Instant.parse("2026-06-05T05:22:58.600Z"),
                "keyboard",
                null,
                null,
                null
        ));

        String jsonLine = Files.readString(outputPath);

        assertThat(jsonLine).contains("\"occurredAt\":\"2026-06-05T05:22:58.600Z\"");
    }
}
