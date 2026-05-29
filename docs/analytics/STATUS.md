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
퍼널 분석
        ↓
BI 대시보드
        ↓
LLM 기반 자동 인사이트 생성
```

현재 구현은 이 중 `사용자 행동 이벤트 수집 -> Kafka 전송` 단계까지다.

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
  "orderId": null
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

### 6. 테스트 안정화

Analytics 테스트와 전체 테스트를 통과시켰다.

```bash
./gradlew test --tests '*Analytics*' --no-daemon -Dorg.gradle.cache.internal.locklistener=false
./gradlew test --no-daemon -Dorg.gradle.cache.internal.locklistener=false
```

추가로 전체 테스트를 위해 아래 기존 문제를 정리했다.

- `OrderInvalidStatusTransitionException` 메시지를 테스트 계약에 맞췄다.
- `src/test/resources/application-test.yml`에 `external-api.order-server.base-url` 기본값을 추가했다.

## 아직 구현하지 않은 것

초기계획서 기준으로 아직 남은 작업은 아래와 같다.

### 1순위: 필수

#### Consumer 구축

아직 Kafka consumer가 없다.

해야 할 일:

- `search-events` consumer
- `product-view-events` consumer
- `cart-events` consumer
- `purchase-events` consumer
- consumer group 설정
- 역직렬화 실패 처리
- 중복 메시지 처리 정책 결정
- 실패 메시지 재처리 또는 DLT 정책 결정

#### Analytics DB 구축

아직 analytics 저장 테이블이 없다.

초기 계획은 서비스 DB와 분석 DB 분리지만, 현재 V1 문서에서는 우선 같은 datasource 안에서 analytics 전용 테이블로 논리 분리하기로 했다.

우선 후보:

- `analytics_events` 단일 테이블

예상 컬럼:

```sql
event_id
event_type
user_id
occurred_at
keyword
product_id
order_id
created_at
```

단일 테이블로 시작하는 이유:

- 현재 이벤트 공통 메시지가 이미 단일 구조다.
- Funnel/KPI 계산을 빠르게 검증하기 좋다.
- 이벤트별 테이블 분리는 조회 패턴이 명확해진 뒤 해도 된다.

#### Funnel 분석

아직 퍼널 분석 API가 없다.

초기계획서 기준 퍼널:

```text
Search
 ↓
Product View
 ↓
Add To Cart
 ↓
Purchase
```

해야 할 일:

- 기간 조건 기반 이벤트 count 조회
- 단계별 count 응답
- 단계 간 전환율 계산
- 빈 데이터, 0 나눗셈 처리
- 사용자 기준 퍼널로 볼지 이벤트 수 기준 퍼널로 볼지 결정

### 2순위: 중요

#### KPI 계산

아직 KPI API가 없다.

초기계획서 기준 KPI:

- Search CTR = Product View / Search
- Cart Rate = Add To Cart / Product View
- Purchase Rate = Purchase / Product View

해야 할 일:

- KPI 조회 API 설계
- 기간 필터
- 상품별 KPI
- 검색어별 KPI
- 카테고리별 KPI

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

### 1. Consumer + Analytics DB 적재

가장 먼저 해야 한다. 현재는 Kafka로 보내기만 하고 데이터가 남지 않기 때문에 분석 기능으로 이어질 수 없다.

권장 범위:

- `analytics_events` 엔티티/테이블
- `AnalyticsEventRepository`
- topic별 `@KafkaListener`
- 메시지 저장 서비스
- consumer 단위/통합 테스트

### 2. Funnel API

저장된 이벤트를 기반으로 초기계획서의 핵심인 퍼널 분석을 먼저 구현한다.

권장 API:

```text
GET /api/analytics/funnel?from=2026-05-01T00:00:00Z&to=2026-05-29T23:59:59Z
```

권장 응답:

```json
{
  "searchCount": 10000,
  "productViewCount": 4000,
  "addToCartCount": 1200,
  "purchaseCount": 300,
  "searchToViewRate": 0.4,
  "viewToCartRate": 0.3,
  "viewToPurchaseRate": 0.075
}
```

### 3. KPI API

퍼널 API 이후 상품/검색어/카테고리 단위 KPI로 확장한다.

### 4. Superset

API보다 BI 경험을 강조하려면 Superset을 붙인다. 단, Superset은 analytics DB에 데이터가 쌓인 뒤 진행하는 것이 맞다.

### 5. Segment + LLM

세그먼트와 LLM은 분석 데이터가 쌓이고 KPI가 나온 뒤 붙이는 것이 자연스럽다.

## 현재 판단

현재 작업은 초기계획서의 1순위 중 `이벤트 모델 설계`, `Kafka 구축`의 producer/API 부분까지 완료된 상태다.

아직 1순위 필수 작업 중 아래는 미완료다.

- Consumer 구축
- Analytics DB 구축
- Funnel 분석

따라서 다음 구현은 Consumer와 Analytics DB 적재를 먼저 진행하는 것이 맞다.
