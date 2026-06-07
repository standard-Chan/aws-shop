package jeong.awsshop.eventpipeline.monolith.domain;

import java.util.List;

/*
 * batch flush 이후 실제 저장소가 구현할 인터페이스다.
 *
 * UserBehaviorEventSink는 요청 thread에서 event를 queue에 넣는 입구이고,
 * UserBehaviorEventStorage는 background worker가 batch를 꺼낸 뒤 호출하는 저장 대상이다.
 */
public interface UserBehaviorEventStorage {

    void saveAll(List<SerializedUserBehaviorEvent> events);
}
