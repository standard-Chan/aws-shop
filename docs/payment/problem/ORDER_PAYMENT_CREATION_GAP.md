# Order 상태 갱신과 Payment 생성 사이 공백 문제

## 문제 정의
- 현재 `create payment` 흐름은 다음 순서다.
  1. Order 서버에 `EXECUTING` 상태 변경 요청
  2. Order 서버가 성공 응답 반환
  3. Payment 서버가 로컬 DB에 `Payment` 생성
- 이 구조에서는 `2`와 `3` 사이에 짧지만 실제로 존재하는 공백이 있다.
- 그 순간 다른 요청이 `orderId` 기준으로 Payment를 조회하면, Order 는 이미 `EXECUTING` 인데 Payment row 는 아직 없어서 실패하게 된다.

## 현재 구조에서 왜 문제가 생기는가
- `Order`와 `Payment`가 서로 다른 저장소와 트랜잭션 경계를 가진다.
- 따라서 `Order=EXECUTING` 과 `활성 Payment 존재`를 하나의 원자적 commit으로 만들 수 없다.
- 지금 구현은 `Order=EXECUTING`을 "활성 Payment가 이미 있다"는 의미로 사용하고 있는데, 실제로는 그 보장이 깨지는 구간이 있다.

## 재현 시나리오
```text
T1: createPayment 요청 시작
T1: Order 서버 updateExecutingStatus 성공
T2: 같은 orderId로 Payment 조회 또는 createPayment 재진입
T2: Order 는 EXECUTING 으로 보임
T2: Payment 조회 결과 없음
T2: 실패 또는 복구 예외 반환
T1: 그 뒤 Payment insert 성공
```

## 핵심 결론
- 이 문제는 단순한 중복 insert 문제가 아니다.
- 더 본질적으로는 "`Order 상태`와 `Payment 존재`를 서로 다른 시스템에서 따로 기록하는데, 읽는 쪽은 둘이 항상 동시에 맞아떨어진다고 가정"한 것이 문제다.
- 해결하려면 다음 둘 중 하나가 필요하다.
  1. 공백 구간에서도 조회 가능한 중간 상태를 만든다.
  2. Payment가 먼저 보이도록 생성 순서를 바꾼다.

## 추가 반론: `선생성 + READY/OK` 만으로는 아직 부족하다

다음 시나리오가 남는다.

```text
T1
1. Payment A 생성 (READY)
2. Order 업데이트 성공

T2
1. Payment B 생성 (READY)
2. Order 업데이트 실패
3. Payment B 삭제
4. "이미 생성된 OK Payment 사용" 시도
   -> 아직 Payment A 가 OK 로 승격되기 전이면 찾지 못함

T1
3. Payment A 를 OK 로 승격
```

이 상황에서 장애가 나는 이유는 `OK Payment가 아직 없기 때문`이 아니다.
진짜 원인은 `Order가 지금 어떤 Payment를 사용해야 하는지 식별할 수 없기 때문`이다.

즉 다음 조건이 필요하다.

- Payment를 먼저 만드는 것
- `READY`도 "현재 유효한 결제 시도"로 해석할 수 있는 것
- 후행 요청이 정확히 같은 Payment attempt를 다시 찾을 수 있는 것
- 새 Payment를 여러 개 만들지 못하게 막는 것

## 해결 방법 후보

### 1. Payment attempt 선생성 후 Order 에 연결 정보 저장
가장 실용적인 방법이다.

#### 흐름
1. Payment 서버에서 먼저 placeholder Payment 생성
2. 상태는 `CREATING` 같은 중간 상태로 저장
3. Payment 는 자신의 `paymentAttemptId`를 가진다
4. Order 서버에 `EXECUTING` 전환 요청을 보낼 때 `paymentAttemptId`도 같이 보낸다
5. Order 는 성공 시 `currentPaymentAttemptId`를 함께 저장한다
6. Payment 는 Order 성공 후 같은 attempt 를 `NOT_STARTED` 로 변경한다
7. Order 전환 실패 시 해당 attempt 를 `ABORTED` 로 변경한다

#### 장점
- 조회 시점에 최소한 Payment row 는 항상 존재한다.
- `Order=EXECUTING` 이전부터 `Payment`가 보이므로 "없어서 실패"하는 공백이 사라진다.
- `Order.currentPaymentAttemptId`로 현재 정답 Payment 를 정확히 찾을 수 있다.
- 과거 검토에서는 `orderId` unique 제약으로 중복 생성을 로컬 DB에서 먼저 제어하는 방식을 고려했다.
- 현재 정책은 실패/성공 결제 이력 보존을 위해 `orderId` unique 제약을 두지 않는다.

#### 단점
- Order 상태 변경 실패 시 보상 로직이 필요하다.
- `CREATING` 상태를 조회 API 와 재시도 로직에서 이해해야 한다.
- Order 서버 계약 변경이 필요하다.

#### 권장 포인트
- 지금 구조에서는 이 방법이 가장 현실적이다.
- 단, `NOT_STARTED`를 바로 쓰지 말고 반드시 `CREATING` 같은 별도 상태를 둬야 한다.
- 그래야 "결제 URL 발급 전"과 "실제 결제 시작 가능"을 구분할 수 있다.
- 더 중요하게는 `orderId`만으로 연결하지 말고 반드시 `paymentAttemptId`로 연결해야 한다.

### 2. 조회/재진입 시 `없는 Payment`를 즉시 실패로 보지 않고 짧게 대기 후 재조회
현재 구조를 많이 바꾸기 어렵다면 가능한 완화책이다.

#### 흐름
1. Order 가 `EXECUTING` 인데 active Payment 가 없으면 즉시 실패하지 않는다.
2. 50ms~200ms 정도 짧게 재시도한다.
3. 재조회에서 Payment 가 생기면 반환한다.
4. 그래도 없으면 복구 또는 재시도 예외를 반환한다.

#### 장점
- 구현 난이도가 낮다.
- 기존 플로우를 크게 바꾸지 않아도 된다.

#### 단점
- 공백을 없애는 것이 아니라 운 좋게 메우는 것이다.
- 지연이 길거나 장애가 나면 여전히 실패한다.
- 트래픽이 높으면 polling 성격의 추가 부하가 생긴다.

#### 평가
- 임시 방편으로는 가능하지만 근본 해결책은 아니다.

### 3. Order 상태 의미를 분리해서 `EXECUTING` 대신 `PAYMENT_CREATING` 중간 상태 도입
상태 모델을 더 정확하게 만드는 방법이다.

#### 흐름 예시
1. `PENDING -> PAYMENT_CREATING`
2. Payment 생성 성공 후 `PAYMENT_CREATING -> EXECUTING`
3. Payment 생성 실패 시 `PAYMENT_CREATING -> PENDING`

#### 장점
- 현재 공백 구간을 도메인 상태로 정직하게 표현할 수 있다.
- 다른 요청이 중간 상태를 보고 "아직 생성 중"으로 처리할 수 있다.

#### 단점
- Order 서버와 Payment 서버 양쪽 상태 머신이 복잡해진다.
- 상태 전이가 늘어나 테스트 범위가 커진다.

#### 평가
- 도메인 의미는 가장 명확하다.
- 다만 이것만으로는 Payment row 부재 자체를 없애지 못하므로, 보통은 `Payment 선생성`과 같이 가는 편이 낫다.

### 4. 하나의 오케스트레이션 저장소로 통합하거나 같은 DB 트랜잭션으로 처리
이론적으로 가장 강한 방법이다.

#### 장점
- `Order 상태 변경`과 `Payment 생성`을 진짜 원자적으로 처리할 수 있다.

#### 단점
- 현재 서버 분리 구조를 사실상 되돌려야 한다.
- 서비스 경계가 무너질 수 있다.

#### 평가
- 지금 아키텍처에서는 비용이 너무 크다.
- 특별한 이유가 없으면 선택하지 않는 편이 낫다.

### 5. 이벤트 기반 비동기 생성
예: Order 가 `EXECUTING` 이벤트를 발행하고 Payment 가 소비해서 생성.

#### 장점
- 서버 간 결합은 낮아진다.
- 재시도와 outbox 패턴을 붙이기 좋다.

#### 단점
- 일관성은 더 eventual 해진다.
- 이벤트 소비 전까지는 Payment 부재가 정상 상황이 된다.
- 조회 API 도 반드시 `생성 중` 상태를 지원해야 한다.

#### 평가
- 확장성은 좋지만 현재 문제의 "즉시 조회 실패"는 더 강하게 드러날 수 있다.
- 동기 조회 UX가 중요하면 단독으로는 부적합하다.

## 추천안

### 추천 1순위
- `Payment attempt 선생성 + Order.currentPaymentAttemptId 연결 + 중간 상태 도입 + 보상 처리`

### 추천 이유
- 지금 문제의 본질은 "조회 시점에 row 자체가 없다"는 점이다.
- 따라서 가장 먼저 해야 할 일은 `Payment row를 먼저 보이게 만드는 것`이다.
- 여기에 `CREATING` 상태를 두면 아직 결제 시작이 완전히 준비되지 않은 구간도 안전하게 표현할 수 있다.
- 하지만 거기서 끝나면 안 되고, 후행 요청이 같은 row를 다시 찾을 수 있도록 `currentPaymentAttemptId` 연결이 있어야 한다.

## 추천 설계 예시

### Payment 상태
- `CREATING`: row는 만들었지만 Order `EXECUTING` 확정 전
- `NOT_STARTED`: 결제 시작 가능
- `EXECUTING`
- `SUCCESS`
- `FAILED`
- `EXPIRED`
- `ABORTED`: Order 전환 실패 등으로 폐기

### Order 필드
- `status`
- `currentPaymentAttemptId`

### 생성 시퀀스
```text
1. Payment 서버
   - orderId 기준 active Payment 조회
   - 없으면 PaymentAttempt A(status=CREATING) insert

2. Payment 서버 -> Order 서버
   - order 상태를 EXECUTING 으로 변경 요청
   - 동시에 currentPaymentAttemptId=A 저장 요청

3. 성공 시
   - Payment A.status = NOT_STARTED
   - 응답 반환

4. 실패 시
   - Payment A.status = ABORTED
   - 적절한 예외 반환
```

### 조회 규칙
- `Order.currentPaymentAttemptId`가 있으면 그 Payment 를 먼저 조회한다.
- `CREATING` 이면 실패 대신 `생성 중` 또는 `잠시 후 재시도`로 응답한다.
- `NOT_STARTED`/`EXECUTING` 이면 기존 active Payment 로 간주한다.
- `ABORTED` 는 active Payment 로 보지 않는다.

### 왜 이 방식이 반론 시나리오를 막는가
```text
T1
1. Payment A 생성 (CREATING)
2. Order.currentPaymentAttemptId = A 로 EXECUTING 성공

T2
1. 새 Payment 생성 전 Order/current active Payment 확인
2. currentPaymentAttemptId=A 확인
3. Payment A 조회
4. A 가 아직 CREATING 이어도 "현재 시도 중인 정답 Payment"로 판단
5. 실패 처리하지 않고 재사용 또는 짧게 재시도

T1
3. Payment A 를 NOT_STARTED 로 승격
```

즉 후행 요청은 `OK Payment가 아직 없음` 때문에 장애로 빠지지 않는다.

## 구현 시 주의점
- `active payment` 판단 기준에 `CREATING` 포함 여부를 명확히 정해야 한다.
- `CREATING` 이 오래 남는 경우를 위한 timeout/정리 배치가 필요하다.
- 보상 실패 시 운영자가 추적할 수 있도록 로그와 메트릭을 남겨야 한다.
- 현재 정책에서는 `orderId` 기준 unique를 제거하고, 새 결제 생성 시 기존 활성 Payment를 `FAILED`로 전환해 최신 활성 결제만 남긴다.
- 가능하면 hard delete 대신 `ABORTED` 같은 상태 전이를 사용해 추적 가능성을 유지한다.

## 최종 정리
- 현재 구조에서 `Order=EXECUTING` 성공 후 `Payment insert`를 하는 한, 공백 문제는 원리상 계속 남는다.
- 짧은 재조회는 완화책일 뿐이다.
- `Payment 선생성`만으로는 부족하고, `Order가 어떤 Payment attempt를 참조하는지`를 식별할 수 있어야 한다.
- 근본 해결은 `Payment를 먼저 보이게 만들고`, `Order.currentPaymentAttemptId`로 연결하며, 그 사이 상태를 `CREATING` 같은 중간 상태로 모델링하는 것이다.
- 따라서 이 프로젝트에서는 `Payment attempt 선생성 + currentPaymentAttemptId 연결 + 중간 상태 + 보상 처리`를 우선 검토하는 것이 가장 적절하다.
