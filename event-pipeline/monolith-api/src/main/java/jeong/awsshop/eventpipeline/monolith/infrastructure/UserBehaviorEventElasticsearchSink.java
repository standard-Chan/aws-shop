package jeong.awsshop.eventpipeline.monolith.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(20)
@ConditionalOnProperty(
        name = "event-pipeline.monolith.elasticsearch.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
public class UserBehaviorEventElasticsearchSink implements UserBehaviorEventStorage {

    /*
     * Elasticsearch Bulk API는 application/json이 아니라 NDJSON을 받는다.
     *
     * 형식:
     * {"index":{"_index":"user-behavior-events","_id":"1"}}
     * {"eventId":1,...}
     * {"index":{"_index":"user-behavior-events","_id":"2"}}
     * {"eventId":2,...}
     *
     * 마지막 줄도 newline으로 끝나야 bulk parser가 안정적으로 읽는다.
     */
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final String indexName;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public UserBehaviorEventElasticsearchSink(
            @Value("${event-pipeline.monolith.elasticsearch.base-url}") String baseUrl,
            @Value("${event-pipeline.monolith.elasticsearch.index-name}") String indexName,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this.indexName = indexName;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public void saveAll(List<SerializedUserBehaviorEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        // ⏱️ 대기 시간 측정을 위한 시작 시간 기록
        long startTime = System.currentTimeMillis();
        int eventCount = events.size();

        log.info("Starting ES bulk request. Event count: {}", eventCount);

        try {
            /*
             * batch마다 단건 PUT을 반복하지 않고 ES _bulk API 한 번으로 저장한다.
             * _id를 eventId로 고정해 같은 이벤트가 재처리되더라도 같은 문서를 덮어쓰게 한다.
             */
            restClient.post()
                .uri("/_bulk")
                .contentType(NDJSON)
                .body(toBulkRequestBody(events))
                .retrieve()
                .toBodilessEntity(); // 💡 여기서 응답이 올 때까지 스레드가 대기(Blocking)합니다.

            // ⏱️ 응답이 도착한 후 걸린 시간 계산
            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully saved user behavior events to ES. Event count: {}, Took: {}ms", eventCount, duration);

        } catch (RestClientException exception) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to save user behavior events to Elasticsearch. Event count: {}, Failure after: {}ms", eventCount, duration, exception);

            throw new IllegalStateException(
                "Failed to save user behavior events to Elasticsearch. eventCount=" + eventCount,
                exception
            );
        }
    }

    private String toBulkRequestBody(List<SerializedUserBehaviorEvent> events) {
        StringBuilder builder = new StringBuilder();
        for (SerializedUserBehaviorEvent event : events) {
            /*
             * Bulk API는 "명령 줄(action line)"과 "문서 줄(source line)"이 한 쌍이다.
             * event.json()은 BatchingUserBehaviorEventSink에서 공통으로 만든 문서 JSON이다.
             */
            builder.append(toBulkActionLine(event)).append('\n');
            builder.append(event.json()).append('\n');
        }
        return builder.toString();
    }

    private String toBulkActionLine(SerializedUserBehaviorEvent event) {
        ObjectNode action = objectMapper.createObjectNode();
        ObjectNode index = action.putObject("index");
        index.put("_index", indexName);
        /*
         * eventId를 ES document id로 사용한다.
         * 이렇게 하면 batch 재시도나 중복 flush 상황에서도 동일 eventId가 여러 문서로 늘어나지 않는다.
         */
        index.put("_id", String.valueOf(event.eventId()));
        return action.toString();
    }
}
