package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserBehaviorEventElasticsearchSinkTest {

    @Test
    @DisplayName("사용자 행동 이벤트를 eventId 문서 ID로 Elasticsearch에 저장해야 한다")
    void shouldSaveUserBehaviorEventToElasticsearchWithEventIdDocumentId() {
        ObjectMapper objectMapper = new EventMonolithJsonConfig().objectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UserBehaviorEventElasticsearchSink sink = new UserBehaviorEventElasticsearchSink(
                "http://localhost:19200",
                "user-behavior-events",
                objectMapper,
                restClientBuilder
        );
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                1L,
                UserBehaviorEventType.PRODUCT_VIEW,
                10L,
                Instant.parse("2026-06-05T05:22:58.600Z"),
                "keyboard",
                100L,
                null,
                999L
        );

        server.expect(once(), requestTo("http://localhost:19200/user-behavior-events/_doc/1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "eventId": 1,
                          "eventType": "PRODUCT_VIEW",
                          "userId": 10,
                          "occurredAt": "2026-06-05T05:22:58.600Z",
                          "keyword": "keyboard",
                          "productId": 100,
                          "orderId": null,
                          "searchEventId": 999
                        }
                        """))
                .andRespond(withSuccess("""
                        {"result":"created"}
                        """, MediaType.APPLICATION_JSON));

        sink.save(event);

        server.verify();
    }
}
