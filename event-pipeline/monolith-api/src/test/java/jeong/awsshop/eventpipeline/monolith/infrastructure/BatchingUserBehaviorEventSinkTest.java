package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.monolith.domain.SerializedUserBehaviorEvent;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BatchingUserBehaviorEventSinkTest {

    @Test
    @DisplayName("save는 이벤트를 큐에 넣고 저장소를 바로 호출하지 않아야 한다")
    void shouldEnqueueEventAndReturnWithoutCallingStorageImmediately() {
        UserBehaviorEventStorage storage = Mockito.mock(UserBehaviorEventStorage.class);
        BatchingUserBehaviorEventSink sink = new BatchingUserBehaviorEventSink(
                1000,
                1000,
                objectMapper(),
                List.of(storage),
                Executors.newSingleThreadScheduledExecutor()
        );

        sink.save(searchEvent(1L));

        assertThat(sink.queuedEventCount()).isEqualTo(1);
        verify(storage, never()).saveAll(anyList());
        sink.stop();
    }

    @Test
    @DisplayName("flush 시 공통 JSON 변환 후 batch 단위로 저장소를 호출해야 한다")
    void shouldSerializeOnceAndSaveBatchOnFlush() {
        CapturingStorage storage = new CapturingStorage();
        BatchingUserBehaviorEventSink sink = new BatchingUserBehaviorEventSink(
                1000,
                1000,
                objectMapper(),
                List.of(storage),
                Executors.newSingleThreadScheduledExecutor()
        );

        sink.save(searchEvent(1L));
        sink.save(searchEvent(2L));
        sink.flushSafely();

        assertThat(storage.savedEvents).hasSize(2);
        assertThat(storage.savedEvents.get(0).eventId()).isEqualTo(1L);
        assertThat(storage.savedEvents.get(0).json()).contains("\"eventId\":1");
        assertThat(storage.savedEvents.get(1).eventId()).isEqualTo(2L);
        assertThat(sink.queuedEventCount()).isZero();
        sink.stop();
    }

    private ObjectMapper objectMapper() {
        return new EventMonolithJsonConfig().objectMapper();
    }

    private UserBehaviorEventMessage searchEvent(Long eventId) {
        return UserBehaviorEventMessage.newMessage(
                eventId,
                UserBehaviorEventType.SEARCH,
                10L,
                Instant.parse("2026-06-05T05:22:58.600Z"),
                "keyboard",
                null,
                null,
                null
        );
    }

    private static class CapturingStorage implements UserBehaviorEventStorage {

        private final List<SerializedUserBehaviorEvent> savedEvents = new ArrayList<>();

        @Override
        public void saveAll(List<SerializedUserBehaviorEvent> events) {
            savedEvents.addAll(events);
        }
    }
}
