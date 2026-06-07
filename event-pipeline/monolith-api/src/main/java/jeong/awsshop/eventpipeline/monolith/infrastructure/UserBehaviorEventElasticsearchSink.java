package jeong.awsshop.eventpipeline.monolith.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
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
public class UserBehaviorEventElasticsearchSink implements UserBehaviorEventStorage {

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
    public void save(UserBehaviorEventMessage event) {
        try {
            restClient.put()
                    .uri("/{indexName}/_doc/{eventId}", indexName, event.eventId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJson(event))
                    .retrieve()
                    .toBodilessEntity();
        } catch (JsonProcessingException | RestClientException exception) {
            throw new IllegalStateException(
                    "Failed to save user behavior event to Elasticsearch. eventId=" + event.eventId(),
                    exception
            );
        }
    }

    private String toJson(UserBehaviorEventMessage event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }
}
