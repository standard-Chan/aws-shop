package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserBehaviorEventElasticsearchSinkTest {

    @Test
    @DisplayName("사용자 행동 이벤트 batch를 Elasticsearch bulk API로 저장해야 한다")
    void shouldSaveUserBehaviorEventBatchToElasticsearchBulkApi() {
        ObjectMapper objectMapper = new EventMonolithJsonConfig().objectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UserBehaviorEventElasticsearchSink sink = new UserBehaviorEventElasticsearchSink(
                "http://localhost:19200",
                "user-behavior-events",
                objectMapper,
                restClientBuilder
        );
        List<SerializedUserBehaviorEvent> events = List.of(
                new SerializedUserBehaviorEvent(
                        1L,
                        """
                                {"eventId":1,"eventType":"PRODUCT_VIEW","userId":10,"occurredAt":"2026-06-05T05:22:58.600Z","keyword":"keyboard","productId":100,"orderId":null,"searchEventId":999}
                                """.trim()
                ),
                new SerializedUserBehaviorEvent(
                        2L,
                        """
                                {"eventId":2,"eventType":"PURCHASE","userId":10,"occurredAt":"2026-06-05T05:23:10Z","keyword":null,"productId":null,"orderId":500,"searchEventId":null}
                                """.trim()
                )
        );

        server.expect(once(), requestTo("http://localhost:19200/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/x-ndjson")))
                .andExpect(content().string("""
                        {"index":{"_index":"user-behavior-events","_id":"1"}}
                        {"eventId":1,"eventType":"PRODUCT_VIEW","userId":10,"occurredAt":"2026-06-05T05:22:58.600Z","keyword":"keyboard","productId":100,"orderId":null,"searchEventId":999}
                        {"index":{"_index":"user-behavior-events","_id":"2"}}
                        {"eventId":2,"eventType":"PURCHASE","userId":10,"occurredAt":"2026-06-05T05:23:10Z","keyword":null,"productId":null,"orderId":500,"searchEventId":null}
                        """))
                .andRespond(withSuccess("""
                        {"errors":false}
                        """, MediaType.APPLICATION_JSON));

        sink.saveAll(events);

        server.verify();
    }
}
