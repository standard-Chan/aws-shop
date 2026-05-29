package jeong.awsshop.analytics.application;

import java.time.Clock;
import java.time.Instant;
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
}
