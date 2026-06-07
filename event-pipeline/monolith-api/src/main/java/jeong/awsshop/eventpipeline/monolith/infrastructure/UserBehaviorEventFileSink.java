package jeong.awsshop.eventpipeline.monolith.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class UserBehaviorEventFileSink implements UserBehaviorEventStorage {

    /*
     * Hadoop/HDFS에 직접 붙기 전 단계의 staging 파일 저장소다.
     *
     * 지금은 로컬 파일에 JSONL을 append한다.
     * JSONL은 "한 줄에 이벤트 하나"라서 Hadoop batch 적재 입력으로 쓰기 쉽고,
     * 파일 끝에 새 batch를 이어 붙이는 방식도 단순하다.
     */
    private static final OpenOption[] APPEND_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
    };

    private final Path outputPath;

    public UserBehaviorEventFileSink(
            @Value("${event-pipeline.monolith.hadoop.output-path}") Path outputPath
    ) {
        this.outputPath = outputPath;
    }

    @Override
    public synchronized void saveAll(List<SerializedUserBehaviorEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        try {
            /*
             * 여러 batch flush가 겹치지 않도록 BatchingUserBehaviorEventSink에서도 막고 있지만,
             * 파일 append는 깨지면 복구가 어렵기 때문에 한 번 더 synchronized로 보호한다.
             */
            createParentDirectories();
            Files.writeString(outputPath, toJsonLines(events), StandardCharsets.UTF_8, APPEND_OPTIONS);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append user behavior event to " + outputPath, exception);
        }
    }

    private void createParentDirectories() throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String toJsonLines(List<SerializedUserBehaviorEvent> events) {
        StringBuilder builder = new StringBuilder();
        for (SerializedUserBehaviorEvent event : events) {
            /*
             * 이미 공통 직렬화된 JSON을 그대로 한 줄로 쓴다.
             * 저장소마다 JSON 변환을 다시 하지 않는 것이 이 batch 구조의 의도다.
             */
            builder.append(event.json()).append(System.lineSeparator());
        }
        return builder.toString();
    }
}
