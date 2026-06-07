package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserBehaviorEventFileSinkTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("사용자 행동 이벤트를 Hadoop staging용 JSONL 파일에 append해야 한다")
    void shouldAppendUserBehaviorEventAsJsonLine() throws Exception {
        Path outputPath = tempDir.resolve("staging/user-behavior-events.jsonl");
        UserBehaviorEventFileSink fileSink = new UserBehaviorEventFileSink(outputPath);

        fileSink.saveAll(List.of(
                new SerializedUserBehaviorEvent(
                        1L,
                        """
                                {"eventId":1,"eventType":"SEARCH","userId":10,"occurredAt":"2026-06-05T05:22:58.600Z","keyword":"keyboard"}
                                """.trim()
                ),
                new SerializedUserBehaviorEvent(
                        2L,
                        """
                                {"eventId":2,"eventType":"ADD_TO_CART","userId":10,"occurredAt":"2026-06-05T05:23:10Z","productId":100}
                                """.trim()
                )
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
