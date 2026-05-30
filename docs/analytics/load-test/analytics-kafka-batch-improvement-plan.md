# Analytics 대용량 이벤트 처리 개선 실험 설계

## 목적

자기소개서에 쓸 수 있는 `문제 -> 병목 분석 -> 개선 -> 수치 결과`를 실제 부하 테스트로 증명한다.

비교 대상은 3단계다.

1. Direct DB 저장: API 서버가 이벤트를 DB에 직접 저장
2. Kafka 단건 Consumer: Kafka 비동기 구조이지만 Consumer가 단건 저장
3. Kafka Batch 개선: batch listener, batch 저장, Consumer concurrency 조정

핵심 지표는 `이벤트 적재 처리량(events/sec)`이다. 보조 지표는 API p95 latency, 실패/미적재 건수, 전체 적재 완료 시간, consumer lag 최대값이다.

## 시간순 작업 계획

1. Docker 기준 실행 환경을 고정한다.
   - Spring Boot, MySQL, Kafka, k6를 같은 로컬 조건에서 실행한다.
   - 테스트 규모는 100,000 이벤트로 고정한다.
   - 각 실험은 최소 3회 반복하고 중앙값을 최종 결과로 사용한다.
2. Kafka topic 조건을 고정한다.
   - 이벤트 topic은 기존 `search-events`, `product-view-events`, `cart-events`, `purchase-events`를 사용한다.
   - concurrency 비교를 위해 topic partition 수를 명시한다.
   - consumer concurrency는 partition 수를 넘지 않도록 설정한다.
3. k6 analytics 부하 테스트를 실행한다.
   - 스크립트: `k6/analytics-events-load-test.js`
   - 이벤트 비율은 `SEARCH 40%`, `PRODUCT_VIEW 35%`, `ADD_TO_CART 20%`, `PURCHASE 5%`로 고정한다.
   - 총 요청 수, 성공/실패 수, avg/p95 latency, 테스트 소요 시간을 summary JSON으로 저장한다.
4. Direct DB 저장 기준선을 측정한다.
   - 벤치마크 전용 API는 Kafka 없이 `analytics_events`에 직접 저장한다.
   - API는 `app.analytics.benchmark.direct-store-enabled=true`일 때만 활성화한다.
   - 측정값:
     - API p95 latency
     - DB 저장 row 수
     - 실패 응답 수
     - 미적재 건수 = 성공 요청 수 - DB row 수
     - 처리량 = DB row 수 / 테스트 소요 시간
5. Kafka 단건 Consumer 기준선을 측정한다.
   - batch 개선 전 커밋에서 Kafka 발행 후 Consumer가 단건 저장하는 구조를 측정한다.
   - 병목 원인은 메시지 1건마다 `existsById()` 조회 후 `save()`를 수행해 DB round-trip이 증가하는 것으로 본다.
   - 측정값:
     - API p95 latency
     - Kafka 발행 성공 수
     - DB 최종 저장 row 수
     - 모든 이벤트 적재 완료 시간
     - consumer lag 최대값
     - 처리량 = DB row 수 / 적재 완료 시간
6. Kafka Batch 개선 후 동일 조건으로 다시 측정한다.
   - Consumer는 batch listener로 메시지 목록을 받는다.
   - 저장 서비스는 batch 단위로 기존 ID를 조회하고 신규 이벤트만 `saveAll`로 저장한다.
   - `AnalyticsStoredEvent`는 `Persistable`을 구현해 신규 이벤트 저장 시 JPA `persist` 경로를 사용한다.
   - Hibernate batch 설정과 Kafka consumer poll/concurrency 설정을 환경 변수로 조정한다.
7. 결과 문서를 작성한다.
   - 원인 분석, 개선 방법, 수치 결과, 자기소개서 문장을 함께 남긴다.
   - 최종 `N%`는 임의값이 아니라 실제 k6, DB, Kafka 측정값으로 계산한다.

## 실행 명령

쓰기 제한 MySQL 실행:

```bash
./scripts/run-analytics-mysql-10mb.sh
```

이 compose는 `aws_shop_test` DB를 만들고 MySQL 컨테이너 쓰기 속도를 기본 `10mb`로 제한한다. 호스트 block device가 다르면 아래처럼 바꿔 실행한다.

```bash
DB_WRITE_LIMIT_DEVICE=/dev/sda ./scripts/run-analytics-mysql-10mb.sh
```

Spring Boot 연결 환경:

```bash
DB=mysql
DB_HOST=localhost
DB_PORT=3307
DB_NAME=aws_shop_test
DB_SETTINGS=serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true
DB_USERNAME=root
DB_PASSWORD=root
```

Direct DB 저장 기준선:

```bash
ANALYTICS_BENCHMARK_DIRECT_STORE_ENABLED=true ./gradlew bootRun
```

```bash
MODE=direct TOTAL_EVENTS=100000 VUS=100 k6 run k6/analytics-events-load-test.js
```

Kafka Batch 개선 측정:

```bash
ANALYTICS_CONSUMER_MAX_POLL_RECORDS=500 \
ANALYTICS_CONSUMER_CONCURRENCY=1 \
HIBERNATE_JDBC_BATCH_SIZE=500 \
./gradlew bootRun
```

```bash
MODE=kafka TOTAL_EVENTS=100000 VUS=100 k6 run k6/analytics-events-load-test.js
```

DB 저장 건수 확인:

```sql
select count(*) from analytics_events;
```

Consumer lag 확인:

```bash
docker exec aws-shop-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group analytics-event-consumer-group
```

## 결과 기록 표

| 구분 | API p95 latency | 실패/미적재 건수 | 적재 완료 시간 | 적재 처리량 | consumer lag 최대값 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Direct DB 저장 | 측정값 | 측정값 | 측정값 | 측정값 | 해당 없음 |
| Kafka 단건 Consumer | 측정값 | 측정값 | 측정값 | 측정값 | 측정값 |
| Kafka Batch 개선 | 측정값 | 측정값 | 측정값 | 측정값 | 측정값 |

개선율 계산식:

```text
처리량 개선율 = (개선 후 events/sec - 개선 전 events/sec) / 개선 전 events/sec * 100
적재 시간 단축률 = (개선 전 적재 완료 시간 - 개선 후 적재 완료 시간) / 개선 전 적재 완료 시간 * 100
p95 개선율 = (개선 전 p95 - 개선 후 p95) / 개선 전 p95 * 100
```

## 자기소개서 문장 템플릿

```text
이커머스 사용자 행동 이벤트를 API 서버에서 DB에 직접 저장하던 구조에서는
10만 건 부하 테스트 시 DB insert 병목으로 p95 latency가 OO ms까지 증가하고,
OO건의 실패/미적재가 발생했습니다.

이를 해결하기 위해 Kafka 기반 비동기 이벤트 파이프라인으로 전환하여
사용자 요청 경로와 분석 데이터 적재 경로를 분리했습니다.
다만 Kafka 적용 후에도 Consumer가 단건 조회/저장을 수행하면서 consumer lag가 증가하는 병목을 확인했습니다.

이후 Kafka batch listener, batch 저장, Consumer concurrency 조정을 적용해
DB round-trip을 줄였고, 이벤트 적재 처리량을 OO events/sec에서 OO events/sec로 개선했습니다.
결과적으로 동일 부하 기준 처리량을 N% 향상시켰습니다.
```

## 기준

- “유실”은 `실패 요청 또는 테스트 종료 후 미적재 건수`로 정의한다.
- Kafka 적용 후 API 성공은 `202 Accepted` 기준이고, 최종 성공은 DB row 적재 완료 기준으로 따로 본다.
- Kafka Batch 개선 후 적재 처리량이 Kafka 단건 Consumer보다 증가해야 한다.
- Consumer lag는 최종적으로 0까지 감소해야 한다.
- 100,000 이벤트 기준 최종 DB row 수가 기대값과 일치해야 한다.
