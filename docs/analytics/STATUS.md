# Analytics 진행 현황

## 현재 목표

이커머스 서비스를 단순 CRUD 쇼핑몰이 아니라 사용자 행동 데이터를 수집, 분석, 활용하는 데이터 기반 백엔드 프로젝트로 확장한다.

초기계획서의 최종 흐름은 아래와 같다.

```text
사용자 행동 이벤트 수집
        ↓
Kafka 전송
        ↓
Consumer 처리
        ↓
Analytics DB 적재
        ↓
퍼널/KPI 분석
        ↓
BI 대시보드
        ↓
LLM 기반 자동 인사이트 생성
```

현재 구현은 이 중 `사용자 행동 이벤트 수집 -> Kafka 전송 -> Consumer 처리 -> Analytics DB 적재 -> 퍼널 분석 -> KPI 분석 API` 단계까지다.

## 완료된 작업

### 1. 이벤트 수집 API

사용자 행동 이벤트를 전용 API로 수집하는 구조를 만들었다.

| 이벤트 | Method | URL | Event Type | Topic |
| --- | --- | --- | --- | --- |
| 검색 | `POST` | `/api/analytics/events/search` | `SEARCH` | `search-events` |
| 상품 상세 조회 | `POST` | `/api/analytics/events/product-view` | `PRODUCT_VIEW` | `product-view-events` |
| 장바구니 추가 | `POST` | `/api/analytics/events/add-to-cart` | `ADD_TO_CART` | `cart-events` |
| 구매 | `POST` | `/api/analytics/events/purchase` | `PURCHASE` | `purchase-events` |

구현 위치:

- `src/main/java/jeong/awsshop/analytics/presentation/AnalyticsEventController.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/*EventRequest.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsEventResponse.java`

현재는 기존 상품/주문/결제 API에서 자동으로 이벤트를 발행하지 않는다. 프론트엔드 또는 호출자가 analytics 전용 API를 직접 호출하는 방식이다.

상품 상세 조회 이벤트는 검색어별 CTR 계산을 위해 선택 필드 `searchEventId`, `searchKeyword`를 추가로 받을 수 있다. 기존 `{ "userId": 1, "productId": 100 }` 요청은 계속 허용한다.

### 2. 이벤트 도메인 모델

Kafka로 발행할 공통 메시지 모델을 만들었다.

- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsEventMessage.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsEventType.java`

공통 메시지 필드:

```json
{
  "eventId": 123,
  "eventType": "SEARCH",
  "userId": 1,
  "occurredAt": "2026-05-29T03:00:00Z",
  "keyword": "macbook",
  "productId": null,
  "orderId": null,
  "searchEventId": null
}
```

`AnalyticsEventMessage`에는 이벤트 타입별 factory를 두었다.

- `search(...)`
- `productView(...)`
- `addToCart(...)`
- `purchase(...)`

`SnowflakeIdGenerator` 자체를 도메인에 넘기지는 않았다. 도메인은 생성된 `eventId`, `occurredAt` 값을 받아 메시지를 구성하고, ID/시간 생성 책임은 application service에 둔다.

### 3. Kafka Producer

Kafka 발행 계층을 추가했다.

- `src/main/java/jeong/awsshop/analytics/application/AnalyticsEventPublisher.java`
- `src/main/java/jeong/awsshop/analytics/infrastructure/KafkaAnalyticsEventPublisher.java`

동작:

- `KafkaTemplate<String, AnalyticsEventMessage>`로 발행한다.
- Kafka key는 `userId` 문자열이다.
- 이벤트 타입별 topic으로 라우팅한다.
- Kafka 발행 실패 시 `AnalyticsEventPublishException`으로 변환한다.
- API 계층은 발행 실패를 `503 Service Unavailable`로 응답한다.

### 4. 설정 및 로컬 실행 기반

Kafka 의존성과 기본 설정을 추가했다.

- `build.gradle`: `org.springframework.kafka:spring-kafka`
- `src/main/resources/application.yml`: Kafka bootstrap server, JSON serializer, topic 설정
- `.env.example`: Kafka 환경 변수 예시
- `compose.yaml`: 로컬 Kafka 컨테이너

기본 topic:

```properties
ANALYTICS_TOPIC_SEARCH=search-events
ANALYTICS_TOPIC_PRODUCT_VIEW=product-view-events
ANALYTICS_TOPIC_CART=cart-events
ANALYTICS_TOPIC_PURCHASE=purchase-events
```

### 5. 문서

Analytics 기준 문서와 API 문서를 추가했다.

- `docs/analytics/ANALYTICS.md`
- `docs/api/analytics-event-controller-api.md`
- `docs/api/analytics-kpi-controller-api.md`

이벤트 API 문서는 Product View 확장 필드와 Kafka 메시지의 `searchEventId`까지 반영했다.

### 6. 테스트 안정화

Analytics 테스트와 전체 테스트를 통과시켰다.

```bash
./gradlew test --tests '*Analytics*' --no-daemon -Dorg.gradle.cache.internal.locklistener=false
./gradlew test --no-daemon -Dorg.gradle.cache.internal.locklistener=false
```

추가로 전체 테스트를 위해 아래 기존 문제를 정리했다.

- `OrderInvalidStatusTransitionException` 메시지를 테스트 계약에 맞췄다.
- `src/test/resources/application-test.yml`에 `external-api.order-server.base-url` 기본값을 추가했다.

### 7. Kafka Consumer와 Analytics DB 적재

2번째 구현으로 Kafka Consumer와 analytics 저장 테이블을 추가했다.

구현 위치:

- `src/main/java/jeong/awsshop/analytics/infrastructure/KafkaAnalyticsEventConsumer.java`
- `src/main/java/jeong/awsshop/analytics/application/AnalyticsEventStoreService.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsStoredEvent.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsEventRepository.java`

동작:

- `search-events`, `product-view-events`, `cart-events`, `purchase-events`를 구독한다.
- Consumer group 기본값은 `analytics-event-consumer-group`이다.
- Consumer는 받은 `AnalyticsEventMessage`를 `analytics_events`에 저장한다.
- `eventId`를 PK로 사용하며, 이미 저장된 이벤트는 정상 처리로 보고 skip한다.
- `createdAt`은 Consumer 저장 시점에 서버 `Clock`으로 생성한다.

운영 DDL:

- 현재 프로젝트는 Flyway/Liquibase를 쓰지 않는다.
- `prod`는 `ddl-auto: validate`이므로 운영 DB에는 `docs/analytics/ANALYTICS.md`의 `analytics_events` DDL을 먼저 적용해야 한다.

### 8. Funnel 분석 API

3번째 구현으로 `analytics_events` 기반 Funnel 분석 조회 API를 추가했다.

구현 위치:

- `src/main/java/jeong/awsshop/analytics/presentation/AnalyticsFunnelController.java`
- `src/main/java/jeong/awsshop/analytics/application/AnalyticsFunnelService.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsEventTypeCount.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsFunnelResponse.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsFunnelStepResponse.java`

동작:

- `GET /api/analytics/funnel?from=...&to=...`로 조회한다.
- `from`, `to`는 필수 ISO-8601 Instant 문자열이다.
- 조회 기준은 `occurred_at >= from AND occurred_at < to`다.
- 기준은 이벤트 수 기반 `EVENT_COUNT`다.
- 단계 순서는 `SEARCH -> PRODUCT_VIEW -> ADD_TO_CART -> PURCHASE`로 고정한다.
- 각 단계별 count, 직전 단계 대비 전환율, 검색 단계 대비 전환율을 반환한다.
- 조회 결과가 없는 단계는 count `0`으로 채우고, 전환율 분모가 `0`이면 `0.0`으로 처리한다.
- `conversionRateFromPrevious`는 첫 단계의 직전 단계가 없음을 표현하기 위해 nullable `Double`을 사용한다.
- `conversionRateFromSearch`는 항상 숫자를 반환하므로 primitive `double`을 사용한다.
- `AnalyticsFunnelService`에는 집계 결과를 고정 퍼널 순서로 재구성하는 의도와 전환율 계산 규칙을 주석으로 남겼다.

테스트/호출 예시:

- `src/test/java/jeong/awsshop/analytics/domain/AnalyticsEventRepositoryTest.java`
- `src/test/java/jeong/awsshop/analytics/application/AnalyticsFunnelServiceTest.java`
- `src/test/java/jeong/awsshop/analytics/presentation/AnalyticsFunnelControllerTest.java`
- `src/test/http/analytics/analytics-funnel-api.http`

검증 결과:

```bash
./gradlew test --tests '*Analytics*' --no-daemon -Dorg.gradle.cache.internal.locklistener=false
./gradlew test --no-daemon -Dorg.gradle.cache.internal.locklistener=false
```

둘 다 통과했다.

### 9. KPI 분석 API

4번째 구현으로 `analytics_events` 기반 KPI 분석 조회 API를 추가했다.

구현 위치:

- `src/main/java/jeong/awsshop/analytics/presentation/AnalyticsKpiController.java`
- `src/main/java/jeong/awsshop/analytics/application/AnalyticsKpiService.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsKpiSummaryResponse.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsProductKpiResponse.java`
- `src/main/java/jeong/awsshop/analytics/presentation/dto/AnalyticsKeywordKpiResponse.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsProductKpiCount.java`
- `src/main/java/jeong/awsshop/analytics/domain/AnalyticsKeywordKpiCount.java`

동작:

- `GET /api/analytics/kpis/summary?from=...&to=...`로 전체 요약 KPI를 조회한다.
- `GET /api/analytics/kpis/products?from=...&to=...&limit=20`로 상품별 KPI를 조회한다.
- `GET /api/analytics/kpis/keywords?from=...&to=...&limit=20`로 검색어별 KPI를 조회한다.
- KPI 기준은 Funnel API와 동일한 이벤트 수 기반 `EVENT_COUNT`다.
- 기간 조건은 `occurred_at >= from AND occurred_at < to`다.
- `from >= to`이면 `400 Bad Request`를 반환한다.
- `limit` 기본값은 `20`, 허용 범위는 `1..100`이다.
- rate 계산에서 분모가 `0`이면 `0.0`으로 처리한다.

Product View 이벤트 확장:

- `ProductViewEventRequest`에 nullable `searchEventId`, `searchKeyword`를 추가했다.
- `AnalyticsEventMessage`와 `AnalyticsStoredEvent`에 nullable `searchEventId`를 추가했다.
- `PRODUCT_VIEW.searchKeyword`는 기존 `keyword` 컬럼에 저장한다.
- 수집 API에서는 `searchEventId`와 `searchKeyword`가 실제 검색 이벤트와 일치하는지 DB로 검증하지 않는다.
- 기존 `{ "userId": 1, "productId": 100 }` 요청도 계속 허용한다.

KPI 계산:

- Summary: `searchCtr = productViewCount / searchCount`
- Summary: `cartRate = addToCartCount / productViewCount`
- Summary: `purchaseRate = purchaseCount / productViewCount`
- Product: `cartRate = addToCartCount / productViewCount`
- Product: `purchaseRate`는 V1에서 `null`이다.
- Keyword: `searchCtr = productViewCount / searchCount`

검증 결과:

```bash
./gradlew test --tests '*Analytics*' --no-daemon -Dorg.gradle.cache.internal.locklistener=false
./gradlew test --no-daemon -Dorg.gradle.cache.internal.locklistener=false
```

통과했다.

테스트/호출 예시:

- `src/test/http/analytics/analytics-event-api.http`
- `src/test/http/analytics/analytics-funnel-api.http`
- `src/test/http/analytics/analytics-kpi-api.http`
- `src/test/http/analytics/analytics-api-test.http`
- `src/test/analytics/analytics-api-test.http`

코드 가독성 보강:

- KPI service/controller/repository 메서드에 짧은 의도 주석을 추가했다.
- 이벤트 메시지 factory와 Product View 선택 필드 검증 메서드에 의도 주석을 추가했다.

## 아직 구현하지 않은 것

초기계획서 기준으로 아직 남은 작업은 아래와 같다.

### 1순위: 필수

#### Consumer 운영 고도화

기본 Consumer와 DB 적재는 구현했다. 아직 운영 고도화는 남아 있다.

해야 할 일:

- 역직렬화 실패 처리 상세 정책 결정
- 실패 메시지 재처리 또는 DLT 정책 결정
- Consumer lag 모니터링
- 운영 Kafka topic 생성/partition/retention 정책 정리

#### Analytics DB 운영 고도화

기본 `analytics_events` 저장 테이블은 구현했다.

초기 계획은 서비스 DB와 분석 DB 분리지만, 현재 V1 문서에서는 우선 같은 datasource 안에서 analytics 전용 테이블로 논리 분리하기로 했다.

단일 테이블로 시작하는 이유:

- 현재 이벤트 공통 메시지가 이미 단일 구조다.
- Funnel/KPI 계산을 빠르게 검증하기 좋다.
- 이벤트별 테이블 분리는 조회 패턴이 명확해진 뒤 해도 된다.

남은 일:

- 운영 DB DDL 적용 방식 자동화
- 데이터 증가에 따른 partition/archive 정책 검토
- 조회 패턴 확정 뒤 이벤트별 테이블 또는 집계 테이블 분리 여부 검토

### 2순위: 중요

#### 사용자 관심사 세그먼트 생성

아직 사용자 세그먼트 기능이 없다.

초기계획서 후보 테이블:

```sql
user_interest_segment

user_id
segment_name
score
updated_at
```

해야 할 일:

- keyword/product/category 기반 segment mapping 규칙 정의
- segment score 계산 방식 정의
- batch로 만들지, consumer 처리 중 갱신할지 결정
- 사용자별 segment 조회 API 또는 BI용 테이블 설계

#### Superset 대시보드

아직 BI 대시보드는 없다.

초기계획서 기준 분석 항목:

- 인기 검색어 TOP N
- 조회수 TOP 상품
- 전환율 TOP 상품
- 카테고리별 구매율
- 시간대별 구매율
- 사용자 세그먼트 분포

해야 할 일:

- Superset 실행 환경 추가
- Analytics DB 연결
- 대시보드용 SQL 또는 view 작성
- README 또는 docs에 접속/실행 방법 정리

### 3순위: 추가

#### LLM 기반 자동 인사이트 생성

아직 LLM 인사이트 기능은 없다.

초기계획서의 목적은 단순 챗봇이 아니라 운영자가 데이터를 이해하도록 돕는 것이다.

해야 할 일:

- KPI/퍼널 결과를 LLM 입력 요약 데이터로 변환
- 인사이트 생성 API 설계
- 프롬프트 템플릿 작성
- 외부 LLM 장애 시 fallback 응답 정책
- 비용/호출 제한 정책

#### 추천 시스템

아직 추천 기능은 없다.

해야 할 일:

- 사용자 세그먼트 기반 추천
- 상품 조회/구매 이력 기반 추천
- bought together 데이터와 analytics 이벤트 결합 가능성 검토

## 다음 작업 추천 순서

### 1. KPI API

전체 요약, 상품별, 검색어별 KPI는 구현했다. 후속 KPI 작업은 product table join이 필요한 카테고리별 KPI다.

우선순위:

1. 카테고리별 KPI
2. 상품별 구매율 계산을 위한 PURCHASE 이벤트 또는 주문 상품 라인 모델링
3. 사용자 수 기반 deduplication KPI

### 2. Superset

API보다 BI 경험을 강조하려면 Superset을 붙인다. 단, Superset은 analytics DB에 데이터가 쌓인 뒤 진행하는 것이 맞다.

### 3. Segment + LLM

세그먼트와 LLM은 분석 데이터가 쌓이고 KPI가 나온 뒤 붙이는 것이 자연스럽다.

## 현재 판단

현재 작업은 초기계획서의 1순위 중 `이벤트 모델 설계`, `Kafka 구축`, `Consumer 처리`, `Analytics DB 적재`, `Funnel 분석`, `KPI 계산 API`까지 완료된 상태다.

아직 2순위 중요 작업 중 아래는 미완료다.

- Superset 대시보드
- 사용자 관심사 세그먼트 생성

따라서 다음 구현은 Superset 대시보드, 사용자 세그먼트, 카테고리별 KPI 중 하나를 선택하는 것이 자연스럽다.
