# Product 상세 조회 API 리팩터링 검토 문서

## 리팩터링 체크리스트

- [x] 1. `ProductReadService#getProductDetail`의 DTO 조립 로직을 `ProductDetailResponse.from(...)` factory로 이동
- [x] 2. `ProductReadService`의 `ObjectMapper` 직접 생성 제거
- [x] 3. `details` JSON 파싱 책임을 `ProductDetailResponse` 내부 factory로 이동
- [ ] 4. `details` JSON 파싱 실패 예외를 구체 클래스로 분리
- [ ] 5. 상세 조회 child collection 조회 로직을 의도 단위 private method로 분리
- [ ] 6. 상세 조회용 response DTO factory 패턴을 기존 summary DTO 패턴과 맞춤
- [ ] 7. `ProductImageResponse`의 summary/detail projection overload 이름을 더 명확하게 분리
- [ ] 8. repository native query의 alias, 정렬 SQL 중복을 상수 또는 문서화된 query block으로 정리
- [ ] 9. 상세 projection interface 파일들의 주석과 명명 기준을 정리
- [x] 10. 예외 메시지 문자열을 상수로 이동
- [ ] 11. `ProductReadService` 생성자 의존성이 과도해진 문제를 검토
- [ ] 12. Controller method 순서를 path 충돌 위험이 낮게 정리

---

## 1. 검토 범위

대상은 `GET /api/products/{id}` 상세 조회 GREEN 구현이다.

주요 파일:

- `ProductController`
- `ProductReadService`
- `ProductRepository`
- `ProductFeatureRepository`
- `ProductDescriptionRepository`
- `ProductCategoryRepository`
- `ProductBoughtTogetherRepository`
- `ProductImageRepository`
- `ProductVideoRepository`
- `ProductDetailResponse`
- 상세 조회용 response DTO
- 상세 조회용 projection interface
- `ProductNotFoundException`

리팩터링 원칙:

- 테스트 코드는 수정하지 않는다.
- 동작은 바꾸지 않는다.
- 테스트는 계속 GREEN이어야 한다.
- 한 번에 하나씩 변경한다.
- 확장성을 위한 새 추상화는 만들지 않는다.

---

## 2. 리팩터링 후보

### 2.0 진행한 리팩터링

이번 단계에서 진행한 항목:

- `ProductNotFoundException`에 id를 받는 생성자를 추가했다.
- Product 상세 조회에서 상품이 없을 때 `id`가 포함된 메시지를 사용한다.
- 기존 테스트가 no-arg 생성자를 직접 사용하고 있으므로 no-arg 생성자는 유지했다.
- `ProductNotFoundException` 메시지 문자열을 상수로 이동했다.
- `ProductReadService#getProductDetail`의 상세 응답 생성 로직을 `ProductDetailResponse.from(...)` factory로 이동했다.
- Service는 Product 본문 조회, child projection 조회, factory 호출 흐름만 담당하도록 줄였다.
- `parseDetailsJson`을 Service에서 제거하고 `ProductDetailResponse` 내부로 이동했다.
- Service가 직접 생성하던 `ObjectMapper` field를 제거했다.

검증:

```bash
./gradlew test --tests '*GetProductDetailTest' --tests '*FindProductDetailTest'
./gradlew test
```

두 명령 모두 GREEN 상태를 확인했다.

### 2.1 `ProductReadService#getProductDetail`이 너무 많은 일을 한다

현재 상태:

- Product 본문 조회
- not found 처리
- child collection 전체 조회
- projection to response 변환
- `details` JSON 파싱
- `ProductDetailResponse` 생성

문제:

- Service가 흐름 제어 외에 DTO 조립과 JSON 변환까지 담당한다.
- method가 길어져 상세 조회 흐름을 한눈에 읽기 어렵다.
- 기존 목록 조회 쪽은 `ProductCursorResponse.from(...)`, `ProductSummaryResponse.from(...)`으로 DTO 조립 책임을 DTO에 둔다.

리팩터링 방향:

- `ProductDetailResponse.from(...)` factory를 만든다.
- Service는 Product와 child projection 목록을 조회하고 factory에 넘긴다.
- `details` 변환도 factory 또는 별도 private helper로 이동한다.

주의:

- 테스트가 현재 `ProductDetailResponse` record 생성자를 직접 사용하고 있으므로 생성자는 유지한다.
- 동작은 유지한다.

우선순위: 높음

---

### 2.2 `ObjectMapper`를 Service 안에서 직접 생성한다

현재 상태:

```java
private final ObjectMapper objectMapper = new ObjectMapper();
```

문제:

- Spring Boot가 관리하는 ObjectMapper 설정을 사용하지 않는다.
- 테스트에서는 통과하지만 운영 설정과 JSON 처리 정책이 달라질 수 있다.
- `@RequiredArgsConstructor`와 final field 조합에서 이 field만 직접 초기화되어 의존성 관리 방식이 섞인다.

리팩터링 방향:

- 생성자 주입으로 `ObjectMapper`를 받는다.
- 이미 Spring Boot에 ObjectMapper bean이 존재하므로 새 bean은 만들지 않는다.

주의:

- Service 단위 테스트가 `@InjectMocks`를 사용한다. Mockito가 ObjectMapper 주입을 못 하면 테스트 수정이 필요할 수 있다.
- 테스트 수정 금지 원칙 때문에 이 리팩터링은 단위 테스트 영향 확인이 필요하다.

우선순위: 높음

---

### 2.3 `details` 파싱 실패 예외가 `IllegalStateException`이다

현재 상태:

```java
throw new IllegalStateException("[Product 상세 조회 실패]: details JSON 파싱에 실패했습니다.", e);
```

문제:

- 메시지는 명확하지만 예외 타입이 일반적이다.
- product read 계열 예외 정책과 분리되어 있다.
- 실패 원인을 테스트나 로그에서 분류하기 어렵다.

리팩터링 방향:

- `ProductDetailsParsingException` 같은 구체 예외를 추가한다.
- 메시지는 상수로 관리한다.
- 원인 exception은 cause로 유지한다.

주의:

- 현재 RED/GREEN 테스트에는 파싱 실패 케이스가 없다.
- 테스트 없는 예외 타입 변경이므로 우선 문서화 후 별도 테스트 추가 여부를 확인해야 한다.

우선순위: 중간

---

### 2.4 child collection 조회와 매핑이 반복된다

현재 상태:

```java
productFeatureRepository.findFeatureDetailsByProductId(id)
        .stream()
        .map(ProductFeatureResponse::from)
        .toList()
```

이 패턴이 6번 반복된다.

문제:

- 상세 응답 생성자 인자 순서를 눈으로 맞춰야 한다.
- collection 추가/삭제 시 실수할 여지가 있다.
- Service method가 길어진다.

리팩터링 방향:

- `findFeatureResponses(id)`, `findDescriptionResponses(id)`처럼 의도 단위 private method로 분리한다.
- 한 번에 모든 추상화를 만들지 말고, method 추출만 한다.

주의:

- private method가 너무 많아질 수 있다.
- 단순 반복이므로 `ProductDetailResponse.from(...)`으로 이동하면 자연스럽게 정리될 수 있다.

우선순위: 중간

---

### 2.5 `ProductImageResponse#from(...)` overload가 projection 타입만 다르다

현재 상태:

```java
public static ProductImageResponse from(ProductSummaryNativeProjection row)
public static ProductImageResponse from(ProductImageDetailProjection row)
```

문제:

- 호출 지점에서는 명확하지만, 클래스만 보면 summary용 null 처리와 detail용 변환 정책이 섞여 있다.
- summary projection은 image 값이 모두 null이면 `null`을 반환한다.
- detail projection은 항상 response를 생성한다.

리팩터링 방향:

- `fromSummary(...)`, `fromDetail(...)`처럼 이름으로 정책 차이를 드러낸다.
- 기존 호출부를 함께 변경한다.

주의:

- 테스트는 method 이름을 직접 참조하지 않는다.
- 동작은 유지해야 한다.

우선순위: 중간

---

### 2.6 repository query 문자열이 커지고 있다

현재 상태:

- `ProductRepository`에 목록 query, category query, 상세 query가 함께 있다.
- child repository에도 native query가 직접 들어 있다.

문제:

- query가 길어질수록 repository interface 가독성이 떨어진다.
- `MAIN` 이미지 우선 정렬 SQL이 summary query와 detail query에 중복된다.
- nulls last 정렬 SQL도 feature/description에서 비슷한 형태로 반복된다.

리팩터링 방향:

- 당장 별도 abstraction은 만들지 않는다.
- query block에 의도를 설명하는 주석을 추가하거나, SQL 상수를 사용할지 검토한다.
- 기존 목록 API refactor 문서의 query 상수화 방향과 맞출 수 있다.

주의:

- Java annotation의 `@Query`는 compile-time constant가 필요하다.
- 상수 분리는 interface 내부 또는 별도 final class가 필요해 구조 변경이 커질 수 있다.

우선순위: 낮음

---

### 2.7 상세 projection interface에 설명이 없다

현재 상태:

- `ProductDetailProjection`
- `ProductFeatureDetailProjection`
- `ProductDescriptionDetailProjection`
- 기타 child projection

문제:

- `ProductSummaryNativeProjection`에는 native query alias 기반 projection이라는 설명이 있다.
- 새 projection들은 어떤 query에서 쓰이는지 설명이 없다.

리팩터링 방향:

- 각 projection 또는 대표 projection에 짧은 주석을 추가한다.
- alias와 getter 이름을 맞춰야 한다는 규칙을 명시한다.

우선순위: 낮음

---

### 2.8 `ProductNotFoundException` 메시지가 생성자에 직접 들어 있다

현재 상태:

```java
super(HttpStatus.NOT_FOUND, "[Product 조회 실패]: 존재하지 않는 상품입니다.");
```

문제:

- refactor 지침의 "예외 메시지 하드코딩 제거"에 맞지 않는다.
- 같은 메시지를 테스트나 다른 예외에서 재사용하기 어렵다.

리팩터링 방향:

- `private static final String MESSAGE`로 분리한다.
- 필요하면 prefix도 상수로 둔다.

우선순위: 높음

---

### 2.9 `ProductReadService` 의존성이 많아졌다

현재 상태:

`ProductReadService`가 아래 repository를 모두 의존한다.

- `ProductRepository`
- `ProductFeatureRepository`
- `ProductDescriptionRepository`
- `ProductCategoryRepository`
- `ProductBoughtTogetherRepository`
- `ProductImageRepository`
- `ProductVideoRepository`

문제:

- Service 생성자 인자가 많아졌다.
- 목록 조회와 상세 조회 의존성이 한 service에 섞였다.
- 상세 조회가 커질수록 service가 비대해질 가능성이 있다.

리팩터링 방향:

- 지금은 기존 `ProductReadService` 사용 요청이 있었으므로 그대로 둔다.
- 추후 상세 조회가 더 커지면 내부 helper component 분리를 검토할 수 있다.

주의:

- "기존 ProductService 사용" 요청과 "확장성을 위한 구조 추가 금지" 때문에 지금 당장 분리하지 않는다.

우선순위: 낮음

---

### 2.10 Controller method 순서와 path 충돌 가독성

현재 상태:

- `GET /api/products`
- `GET /api/products/category`
- `GET /api/products/{id}`

문제:

- Spring MVC는 static path를 우선 매칭하므로 동작 문제는 없다.
- 다만 사람이 볼 때 `/{id}`가 더 위로 올라가면 `/category`와 충돌하는지 오해할 수 있다.

리팩터링 방향:

- 현재처럼 `category`를 먼저 두고 `/{id}`를 뒤에 둔다.
- 주석에 기존 category path와 공존한다는 의도를 짧게 남길 수 있다.

우선순위: 낮음

---

## 3. 우선순위 제안

먼저 할 만한 항목:

1. `ProductNotFoundException` 메시지 상수화
2. `ProductDetailResponse.from(...)` factory 추가
3. `getProductDetail`의 긴 생성자 호출 제거
4. `ObjectMapper` 직접 생성 제거 여부 검토

나중에 해도 되는 항목:

1. query 문자열 정리
2. projection 주석 추가
3. Service 의존성 분리 검토
4. Controller path 공존 주석 추가

---

## 4. 보류할 항목

현재 단계에서 바로 하지 않는 편이 나은 항목:

- `ProductReadService`를 여러 service로 분리
- query 전용 abstraction 추가
- JSON aggregation으로 조회 전략 변경
- 테스트 구조 변경

이 항목들은 동작 변경 또는 구조 변경 폭이 커서 현재 refactor 단계 원칙과 맞지 않는다.
