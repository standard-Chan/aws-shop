package jeong.awsshop.eventpipeline.monolith.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class UserBehaviorEventFileSink implements UserBehaviorEventStorage {

    private static final OpenOption[] APPEND_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
    };

    private final Path outputPath;
    private final ObjectMapper objectMapper;

    public UserBehaviorEventFileSink(
            @Value("${event-pipeline.monolith.hadoop.output-path}") Path outputPath,
            ObjectMapper objectMapper
    ) {
        this.outputPath = outputPath;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(UserBehaviorEventMessage event) {
        try {
            createParentDirectories();
            Files.writeString(outputPath, toJsonLine(event), StandardCharsets.UTF_8, APPEND_OPTIONS);
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

    private String toJsonLine(UserBehaviorEventMessage event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event) + System.lineSeparator();
    }
}
