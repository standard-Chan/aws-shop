# StockController API 문서

`StockController`는 상품 재고 차감과 추가 API를 제공한다.

- Base URL: `/api/stocks`
- Content-Type: `application/json`
- Controller 위치: `src/main/java/jeong/awsshop/stock/presentation/StockController.java`
- HTTP 호출 예시: `src/test/http/stock/stock-api.http`

## 공통 규칙

### 요청 바디

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `quantity` | int | Y | 변경할 재고 수량. 1 이상이어야 한다. |

### 응답 바디

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `productId` | long | 재고가 변경된 상품 ID |
| `quantity` | int | 변경 후 남은 재고 수량 |

### 예외 응답

| 상태 코드 | 예외 | 설명 |
| --- | --- | --- |
| `400 Bad Request` | `InvalidStockQuantityException` | 요청 수량이 0 이하 |
| `404 Not Found` | `StockNotFoundException` | 차감 대상 재고가 없음 |
| `404 Not Found` | `StockProductNotFoundException` | 추가 대상 상품이 없음 |
| `409 Conflict` | `InsufficientStockException` | 차감 요청 수량보다 재고가 부족 |
| `409 Conflict` | `StockQuantityOverflowException` | 재고 추가 후 `int` 범위 초과 |

## 1. 재고 차감

### 요청

- Method: `POST`
- URL: `/api/stocks/{productId}/decrease`

### 요청 예시

```json
{
  "quantity": 2
}
```

### 응답 예시

```json
{
  "productId": 10,
  "quantity": 8
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/stocks/10/decrease' \
  -H 'Content-Type: application/json' \
  -d '{
    "quantity": 2
  }'
```

## 2. 재고 추가

### 요청

- Method: `POST`
- URL: `/api/stocks/{productId}/increase`

### 요청 예시

```json
{
  "quantity": 5
}
```

### 응답 예시

```json
{
  "productId": 10,
  "quantity": 15
}
```

### 호출 예시

```bash
curl -X POST \
  'http://localhost:8080/api/stocks/10/increase' \
  -H 'Content-Type: application/json' \
  -d '{
    "quantity": 5
  }'
```
