package jeong.awsshop.eventpipeline.monolith.infrastructure;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventMessage;
import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.monolith.domain.UserBehaviorEventStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CompositeUserBehaviorEventSinkTest {

    @Test
    @DisplayName("등록된 저장소들을 순서대로 호출해야 한다")
    void shouldCallStoragesInOrder() {
        UserBehaviorEventStorage firstStorage = mock(UserBehaviorEventStorage.class);
        UserBehaviorEventStorage secondStorage = mock(UserBehaviorEventStorage.class);
        CompositeUserBehaviorEventSink sink = new CompositeUserBehaviorEventSink(
                List.of(firstStorage, secondStorage)
        );
        UserBehaviorEventMessage event = UserBehaviorEventMessage.newMessage(
                1L,
                UserBehaviorEventType.SEARCH,
                10L,
                Instant.parse("2026-06-05T05:22:58.600Z"),
                "keyboard",
                null,
                null,
                null
        );

        sink.save(event);

        InOrder inOrder = inOrder(firstStorage, secondStorage);
        inOrder.verify(firstStorage).save(event);
        inOrder.verify(secondStorage).save(event);
    }
}
