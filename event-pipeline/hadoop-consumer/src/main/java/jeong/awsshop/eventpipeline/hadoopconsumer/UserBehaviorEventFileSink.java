package jeong.awsshop.eventpipeline.hadoopconsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserBehaviorEventFileSink {

    private final ObjectMapper objectMapper;
    private final Path outputPath;

    private final ReentrantLock lock = new ReentrantLock();

    public UserBehaviorEventFileSink(
        @Value("${event-pipeline.hadoop.output-path:/tmp/aws-shop-event-pipeline/user-behavior-events.jsonl}")
        Path outputPath,
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.outputPath = outputPath;
    }

    public void appendBatch(List<UserBehaviorEventMessage> messages) {

        lock.lock();

        try {

            Path parent = outputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            StringBuilder buffer = new StringBuilder(messages.size() * 256);

            for (UserBehaviorEventMessage message : messages) {
                buffer.append(
                    objectMapper.writeValueAsString(message)
                ).append(System.lineSeparator());
            }

            Files.writeString(
                outputPath,
                buffer.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                "[HadoopConsumer] batch append failed. path=" + outputPath,
                exception
            );

        } finally {
            lock.unlock();
        }
    }
}