package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserBehaviorEventFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("사용자 행동 이벤트를 Hadoop staging용 JSONL 파일에 append해야 한다")
    void shouldAppendUserBehaviorEventAsJsonLine() throws Exception {
        ObjectMapper objectMapper = new EventMonolithJsonConfig().objectMapper();
        Path outputPath = tempDir.resolve("staging/user-behavior-events.jsonl");
        UserBehaviorEventFileSink fileSink = new UserBehaviorEventFileSink(outputPath, objectMapper);

        fileSink.save(UserBehaviorEventMessage.newMessage(
                1L,
                UserBehaviorEventType.SEARCH,
                10L,
                Instant.parse("2026-06-05T05:22:58.600Z"),
                "keyboard",
                null,
                null,
                null
        ));
        fileSink.save(UserBehaviorEventMessage.newMessage(
                2L,
                UserBehaviorEventType.ADD_TO_CART,
                10L,
                Instant.parse("2026-06-05T05:23:10Z"),
                null,
                100L,
                null,
                null
        ));

        List<String> jsonLines = Files.readAllLines(outputPath);

        assertThat(jsonLines).hasSize(2);
        assertThat(jsonLines.get(0)).contains("\"eventId\":1");
        assertThat(jsonLines.get(0)).contains("\"eventType\":\"SEARCH\"");
        assertThat(jsonLines.get(0)).contains("\"occurredAt\":\"2026-06-05T05:22:58.600Z\"");
        assertThat(jsonLines.get(1)).contains("\"eventId\":2");
        assertThat(jsonLines.get(1)).contains("\"eventType\":\"ADD_TO_CART\"");
        assertThat(jsonLines.get(1)).contains("\"productId\":100");
    }
}
