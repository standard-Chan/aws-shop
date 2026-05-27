# k6 Load Tests

## 목적
- `POST /api/payments`에 동일한 `orderId`로 동시 요청을 보내 중복 결제 생성 방어를 검증한다.
- 목표 상태는 동일한 주문에 대해 활성 결제(`EXECUTING`으로 이어질 수 있는 생성 성공)가 하나만 만들어지는 것이다.
- `orderId` `1,2,3,4,5`를 순서대로 라운드 처리하며, 각 라운드의 기대 응답 분포는 `200` 1건, `409` 49건, `500` 0건이다.

## 시나리오
- 파일: `payment-create-duplicate-guard.js`
- 라운드 수: `5`
- 라운드별 주문 ID: `1,2,3,4,5`
- 라운드별 동시 요청 수: `50`
- 대상 API: `POST /api/payments`

## 수집 지표
- `200 성공 응답 개수`
- `409 응답 개수`
- `500 응답 개수`
- `p95`
- `평균 처리 시간`
- `총 요청 횟수`

## 실행
```bash
k6 run k6/payment-create-duplicate-guard.js
```

## 환경 변수
```bash
BASE_URL=http://localhost:8080 \
ORDER_IDS=1,2,3,4,5 \
VUS=50 \
ROUND_GAP_SECONDS=5 \
EXPECTED_SUCCESS=1 \
EXPECTED_CONFLICT=49 \
EXPECTED_SERVER_ERROR=0 \
k6 run k6/payment-create-duplicate-guard.js
```

## 결과
- 콘솔에 전체 응답 코드별 카운트와 평균 처리 시간, `p95`, 총 요청 수, 주문별 응답 분포가 출력된다.
- 실행 후 `k6/results/payment-create-duplicate-guard-summary.json`에 요약 결과가 저장된다.

## 해석
- 이 테스트는 현재 구현이 "주문당 결제 1개만 생성" 규칙을 각 주문 라운드마다 만족하는지 검증한다.
- 현재 애플리케이션이 `409 Conflict`를 명시적으로 반환하지 않으면 테스트는 실패한다.
- 즉, 이 테스트는 부하 시나리오 자체와 함께 필요한 서버 동작 계약도 고정한다.
