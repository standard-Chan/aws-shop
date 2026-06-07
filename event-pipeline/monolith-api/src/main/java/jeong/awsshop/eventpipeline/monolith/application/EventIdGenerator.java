package jeong.awsshop.eventpipeline.monolith.application;

import jeong.awsshop.eventpipeline.common.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventIdGenerator {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public EventIdGenerator(@Value("${event-pipeline.monolith.node-id:1}") long nodeId) {
        this.snowflakeIdGenerator = new SnowflakeIdGenerator(nodeId);
    }

    public long nextId() {
        return snowflakeIdGenerator.nextId();
    }
}
