package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jeong.awsshop.analytics.domain.AnalyticsEventMessage;
import jeong.awsshop.analytics.domain.AnalyticsEventRepository;
import jeong.awsshop.analytics.domain.AnalyticsStoredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Consumer가 받은 analytics 이벤트를 DB에 적재하는 application service다.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsEventStoreService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final Clock clock;

    /**
     * Consumer가 메시지를 받을 때 호출되며, eventId가 이미 있으면 중복 저장하지 않는다.
     */
    @Transactional
    public void saveIfAbsent(AnalyticsEventMessage message) {
        if (analyticsEventRepository.existsById(message.eventId())) {
            return;
        }

        try {
            analyticsEventRepository.save(AnalyticsStoredEvent.from(message, Instant.now(clock)));
        } catch (DataIntegrityViolationException ignored) {
            // A duplicate event may be delivered concurrently. Treat it as already processed.
        }
    }

    /**
     * Batch listener가 받은 이벤트를 한 트랜잭션에서 저장한다.
     * 기존 ID는 한 번의 조회로 걸러내고, 신규 엔티티는 persist 경로로 저장해 per-row select를 줄인다.
     */
    @Transactional
    public void saveAllIfAbsent(Collection<AnalyticsEventMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        Set<Long> eventIds = messages.stream()
                .map(AnalyticsEventMessage::eventId)
                .collect(Collectors.toSet());
        Set<Long> existingEventIds = analyticsEventRepository.findAllById(eventIds).stream()
                .map(AnalyticsStoredEvent::getEventId)
                .collect(Collectors.toSet());
        Instant createdAt = Instant.now(clock);
        List<AnalyticsStoredEvent> events = messages.stream()
                .filter(message -> !existingEventIds.contains(message.eventId()))
                .map(message -> AnalyticsStoredEvent.from(message, createdAt))
                .toList();

        if (events.isEmpty()) {
            return;
        }

        try {
            analyticsEventRepository.saveAll(events);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent duplicate delivery can still happen between the pre-check and insert.
            // The next retry will skip already stored eventIds.
        }
    }
}
