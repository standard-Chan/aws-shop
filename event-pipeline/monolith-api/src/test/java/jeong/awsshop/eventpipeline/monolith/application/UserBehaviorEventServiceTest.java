package jeong.awsshop.eventpipeline.monolith.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventSink;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.ProductViewEventRequest;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.SearchEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserBehaviorEventServiceTest {

    @Mock
    private EventIdGenerator eventIdGenerator;

    @Mock
    private UserBehaviorEventSink eventSink;

    private UserBehaviorEventService userBehaviorEventService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Clock clock = Clock.fixed(Instant.parse("2026-06-07T06:00:00Z"), ZoneOffset.UTC);
        userBehaviorEventService = new UserBehaviorEventService(eventIdGenerator, eventSink, clock);
    }

    @Test
    @DisplayName("검색 이벤트는 서버 생성 필드를 채워 sink에 저장해야 한다")
    void should_save_search_event_with_server_generated_fields() {
        when(eventIdGenerator.nextId()).thenReturn(100L);

        var response = userBehaviorEventService.recordSearch(new SearchEventRequest(1L, "macbook"));

        ArgumentCaptor<UserBehaviorEventMessage> eventCaptor = ArgumentCaptor.forClass(UserBehaviorEventMessage.class);
        verify(eventSink).save(eventCaptor.capture());

        UserBehaviorEventMessage event = eventCaptor.getValue();
        assertThat(response.eventId()).isEqualTo(100L);
        assertThat(response.eventType()).isEqualTo(UserBehaviorEventType.SEARCH);
        assertThat(event.eventId()).isEqualTo(100L);
        assertThat(event.eventType()).isEqualTo(UserBehaviorEventType.SEARCH);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-06-07T06:00:00Z"));
        assertThat(event.keyword()).isEqualTo("macbook");
        assertThat(event.productId()).isNull();
        assertThat(event.orderId()).isNull();
        assertThat(event.searchEventId()).isNull();
    }

    @Test
    @DisplayName("상품 조회 이벤트는 검색 컨텍스트를 함께 sink에 저장해야 한다")
    void should_save_product_view_event_with_search_context() {
        when(eventIdGenerator.nextId()).thenReturn(101L);

        var response = userBehaviorEventService.recordProductView(
                new ProductViewEventRequest(1L, 200L, 100L, "macbook")
        );

        ArgumentCaptor<UserBehaviorEventMessage> eventCaptor = ArgumentCaptor.forClass(UserBehaviorEventMessage.class);
        verify(eventSink).save(eventCaptor.capture());

        UserBehaviorEventMessage event = eventCaptor.getValue();
        assertThat(response.eventId()).isEqualTo(101L);
        assertThat(response.eventType()).isEqualTo(UserBehaviorEventType.PRODUCT_VIEW);
        assertThat(event.eventType()).isEqualTo(UserBehaviorEventType.PRODUCT_VIEW);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.productId()).isEqualTo(200L);
        assertThat(event.searchEventId()).isEqualTo(100L);
        assertThat(event.keyword()).isEqualTo("macbook");
    }
}
