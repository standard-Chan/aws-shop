package jeong.awsshop.eventpipeline.producer;

import jeong.awsshop.eventpipeline.common.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventIdGenerator {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public EventIdGenerator(@Value("${event-pipeline.producer.node-id:1}") long nodeId) {
        this.snowflakeIdGenerator = new SnowflakeIdGenerator(nodeId);
    }

    public long nextId() {
        return snowflakeIdGenerator.nextId();
    }
}
