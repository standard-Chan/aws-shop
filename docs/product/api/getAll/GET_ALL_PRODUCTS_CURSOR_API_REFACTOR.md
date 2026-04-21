# Product 목록 Cursor 조회 API 리팩터링 검토 문서

**작성일**: 2026-04-21  
**대상**: `GET /api/products` cursor pagination API  
**참고 문서**: `docs/TDD/refactor.md`  
**상태**: 리팩터링 진행 완료

---

## 0. 리팩터링 체크리스트

- [x] 2.1 size 검증 책임을 Controller Bean Validation으로 이동
- [x] 2.2 DTO 매핑 로직을 DTO 내부 factory로 이동
- [x] 3.1 `ProductSummaryNativeProjection`을 repository projection 패키지로 이동
- [x] 3.2 Repository native query를 SQL 상수로 분리
- [x] 3.3 `size + 1` 의미를 `queryLimitForHasNext` 메서드로 분리
- [x] 3.4 `hasNext`, `nextCursorId` 계산을 `ProductCursorResponse.from` factory로 이동
- [x] 3.5 `ProductController`의 `int size`는 유지하고 `@Min`, `@Max`로 검증 의도 명시
- [x] 3.7 설계/RED 문서의 repository method 이름을 실제 코드와 맞춤

---

## 1. 리팩터링 원칙

이번 단계에서는 테스트 코드를 절대 수정하지 않는다.

리팩터링은 다음 원칙을 따른다.

- 한 번에 하나씩만 변경한다.
- 테스트는 계속 GREEN을 유지한다.
- 동작은 변경하지 않는다.
- 새 기능을 추가하지 않는다.
- 요청하지 않은 인터페이스/추상화는 추가하지 않는다.
- Service는 흐름 제어에 집중한다.
- DTO 생성 로직은 DTO 내부로 이동한다.
- 검증 책임은 가능한 진입 계층에서 처리한다.

---

## 2. 사용자가 확인한 리팩터링 항목

### 2.1 size 검증 책임을 Controller로 이동

현재 상태:

- `ProductReadService#getProducts(int size, Long cursor)` 내부에서 `validateSize(size)`를 호출한다.
- invalid size이면 `ResponseStatusException(HttpStatus.BAD_REQUEST)`를 직접 던진다.

문제:

- `size`는 HTTP query parameter 검증에 가까운 값이다.
- service가 HTTP 상태 코드인 `HttpStatus.BAD_REQUEST`와 `ResponseStatusException`에 의존한다.
- service 테스트가 웹 계층 예외 타입을 알아야 한다.
- service의 책임이 cursor 조회 흐름과 request validation으로 섞여 있다.

리팩터링 방향:

- `spring-boot-starter-validation`의 Bean Validation을 사용한다.
- `ProductController`에 `@Validated`를 붙인다.
- `size` parameter에 `@Min(1)`, `@Max(100)`을 적용한다.
- `ProductReadService#validateSize`는 제거한다.
- service는 검증이 끝난 정상 size만 받는다고 가정한다.

예상 코드 방향:

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    @GetMapping
    public ProductCursorResponse getProducts(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long cursor
    ) {
        return productReadService.getProducts(size, cursor);
    }
}
```

주의:

- 테스트 코드는 수정하지 않는다.
- 현재 controller 테스트에는 size 검증 테스트가 없다.
- service 테스트에는 size 검증 테스트가 남아 있으므로, 실제 리팩터링 착수 전 테스트 정책 확인이 필요하다.
- `docs/TDD/refactor.md`는 테스트 수정 금지를 명시하므로, service size 검증 테스트가 존재하는 상태에서 service 검증을 제거하면 테스트가 깨진다.

결정 필요:

- service size 검증 테스트를 유지해야 한다면 이 리팩터링은 현재 테스트와 충돌한다.
- 테스트 수정 금지 원칙을 지키려면, 먼저 사용자가 service size 검증 테스트 제거 또는 controller 검증 테스트 재작성 여부를 명시해야 한다.

결정
- size 검증 테스트는 service에서 제거한다.
- controller에 size 검증 테스트를 추가한다.

### 2.2 DTO 매핑 로직을 DTO 내부 factory로 이동

현재 상태:

- `ProductReadService`가 `ProductSummaryNativeProjection`을 `ProductSummaryResponse`로 변환한다.
- `ProductReadService`가 image projection null 여부를 판단하고 `ProductImageResponse`를 생성한다.

문제:

- service가 조회 흐름, pagination 계산, DTO 변환을 모두 담당한다.
- `ProductReadService#toResponse`와 `ProductReadService#toImageResponse`는 response 생성 책임이다.
- image null 판단 조건이 service 내부에 노출되어 있다.
- DTO 필드가 바뀌면 service가 함께 변경된다.

리팩터링 방향:

- `ProductSummaryResponse`에 static factory를 추가한다.
- `ProductImageResponse`에 static factory를 추가한다.
- service는 `ProductSummaryResponse.from(row)`만 호출한다.
- image null 판단은 `ProductImageResponse.from(row)`에서 처리한다.

예상 코드 방향:

```java
public record ProductSummaryResponse(...) {

    public static ProductSummaryResponse from(ProductSummaryNativeProjection row) {
        return new ProductSummaryResponse(
                row.getId(),
                row.getParentAsin(),
                row.getTitle(),
                MainCategory.valueOf(row.getMainCategory()),
                row.getAverageRating(),
                row.getRatingNumber(),
                row.getPrice(),
                row.getStore(),
                ProductImageResponse.from(row)
        );
    }
}
```

```java
public record ProductImageResponse(...) {

    public static ProductImageResponse from(ProductSummaryNativeProjection row) {
        if (row.getImageVariant() == null
                && row.getImageThumb() == null
                && row.getImageLarge() == null
                && row.getImageHiRes() == null) {
            return null;
        }

        return new ProductImageResponse(
                row.getImageVariant(),
                row.getImageThumb(),
                row.getImageLarge(),
                row.getImageHiRes()
        );
    }
}
```

리팩터링 후 service 방향:

```java
List<ProductSummaryResponse> products = rows.stream()
        .limit(size)
        .map(ProductSummaryResponse::from)
        .toList();
```

주의:

- 동작은 바꾸지 않는다.
- `MainCategory.valueOf(row.getMainCategory())` 동작도 그대로 유지한다.
- enum 변환 실패 케이스는 이번 리팩터링에서 새로 처리하지 않는다.

---

## 3. 추가로 발견한 리팩터링 후보

### 3.1 `ProductSummaryNativeProjection` 패키지 위치

현재 상태:

- `ProductSummaryNativeProjection`이 `service.productread.dto` 패키지에 있다.

문제:

- 이 타입은 response DTO라기보다 repository native query 결과 projection이다.
- service response DTO와 persistence projection이 같은 패키지에 섞여 있다.
- `ProductRepository`가 service dto 패키지에 의존한다.

리팩터링 방향:

- projection을 repository 쪽 패키지로 이동한다.
- 예: `jeong.awsshop.product.repository.projection.ProductSummaryNativeProjection`

주의:

- 테스트 import가 현재 service dto 패키지를 참조하고 있으므로 테스트 수정 없이 바로 옮기면 깨진다.
- 테스트 수정 금지 조건에서는 지금 당장 진행하기 어렵다.
- 리팩터링 대상으로 기록하되, 테스트 변경 허용 시점에 처리한다.

우선순위:

- 중간

### 3.2 Repository native query가 너무 길다

현재 상태:

- `ProductRepository` 안에 긴 native query 문자열이 직접 들어 있다.

문제:

- repository interface 가독성이 낮다.
- SQL 변경 시 Java annotation 문자열을 직접 수정해야 한다.
- query intent와 projection method가 한 화면에서 과하게 길다.

리팩터링 방향:

- 지금은 테스트 통과를 우선하므로 유지한다.
- 추후에는 다음 중 하나를 검토한다.
  - repository custom implementation으로 이동
  - named native query로 분리
  - SQL 상수를 별도 클래스에 둠

주의:

- `docs/TDD/refactor.md`는 요청하지 않은 추상화 추가를 금지한다.
- 따라서 이번 리팩터링에서는 바로 분리하지 않는 편이 안전하다.

우선순위:

- 낮음

### 3.3 `ProductReadService`의 `size + 1` 의미가 상수/메서드로 드러나지 않음

현재 상태:

```java
productRepository.findProductSummaries(cursor, size + 1);
```

문제:

- `size + 1`은 hasNext 판단을 위한 의도 있는 값이다.
- 현재도 이해 가능하지만, 의미를 드러내는 이름이 없다.

리팩터링 방향:

- 작은 private method로 분리한다.

예상 코드 방향:

```java
private int queryLimitForHasNext(int size) {
    return size + 1;
}
```

주의:

- 이 변경은 동작을 바꾸지 않는다.
- 다만 현재 코드가 충분히 단순하므로 우선순위는 낮다.

우선순위:

- 낮음

### 3.4 `hasNext`, `nextCursorId` 계산을 응답 factory로 모을 수 있음

현재 상태:

- service가 `hasNext`, `products`, `nextCursorId`를 직접 계산한다.

문제:

- service에 응답 조립 절차가 남아 있다.
- cursor response 생성 규칙이 `ProductReadService`에 고정되어 있다.

리팩터링 방향:

- `ProductCursorResponse.from(rows, size)` factory를 둘 수 있다.
- service는 repository 호출 후 `ProductCursorResponse.from(rows, size)`만 반환한다.

예상 코드 방향:

```java
public static ProductCursorResponse from(List<ProductSummaryNativeProjection> rows, int size) {
    boolean hasNext = rows.size() > size;
    List<ProductSummaryResponse> products = rows.stream()
            .limit(size)
            .map(ProductSummaryResponse::from)
            .toList();
    Long nextCursorId = products.isEmpty() ? null : products.getLast().id();
    return new ProductCursorResponse(products, nextCursorId, hasNext);
}
```

주의:

- 사용자가 요청한 "Response나 ImageResponse 매핑을 dto 내부 factory로 이전" 범위에 잘 맞는다.
- 이 변경을 하면 service가 가장 단순해진다.

우선순위:

- 높음

### 3.5 `ProductController`가 `int size`를 직접 받음

현재 상태:

- `@RequestParam(defaultValue = "20") int size`

문제:

- query parameter가 primitive라 null과 미입력 구분은 defaultValue에 의존한다.
- 현재 요구에서는 문제 없지만, validation annotation을 붙이면 controller가 더 명시적이어야 한다.

리팩터링 방향:

- 현재는 유지한다.
- `@Min`, `@Max`를 붙이는 정도만 적용한다.

우선순위:

- 낮음

진행 결과:

- `int size`는 유지한다.
- `@RequestParam(defaultValue = "20") @Min(1) @Max(100)`로 검증 범위를 명시했다.

---

## 4. 권장 리팩터링 순서

테스트 코드 수정 금지 조건을 고려하면 아래 순서가 안전하다.

### 1단계: DTO factory 이동

대상:

- `ProductCursorResponse`
- `ProductSummaryResponse`
- `ProductImageResponse`
- `ProductReadService`

작업:

- response 생성 static factory 추가
- service의 `toResponse`, `toImageResponse` 제거
- service는 repository 호출과 factory 호출만 담당

예상 영향:

- 테스트 수정 없음
- 기존 service 테스트 GREEN 유지 가능

### 2단계: size 검증 이동 전 테스트 충돌 확인

대상:

- `ProductReadServiceTest`
- `ProductController`
- `ProductReadService`

문제:

- 현재 service 테스트가 size 검증을 직접 기대한다.
- 테스트 수정 금지 조건에서는 service 검증 제거가 테스트 실패를 만든다.

필요 결정:

- 사용자가 service size 검증 테스트를 제거하거나 controller 검증 테스트로 대체하는 것을 허용해야 한다.
- 테스트를 절대 수정하지 않는다면 size 검증을 service에서 완전히 제거할 수 없다.

### 3단계: Controller Bean Validation 적용

대상:

- `build.gradle`
- `ProductController`
- `ProductReadService`

작업:

- `spring-boot-starter-validation` 의존성은 이미 존재한다.
- `ProductController`에 `@Validated` 추가
- `size`에 `@Min(1)`, `@Max(100)` 추가
- service의 `validateSize` 제거
- service의 `ResponseStatusException`, `HttpStatus` 의존 제거

예상 영향:

- controller가 request validation 책임을 가진다.
- service가 웹 예외 타입에서 분리된다.

### 4단계: 문서 정리

대상:

- `GET_ALL_PRODUCTS_CURSOR_API_DESIGN.md`
- `GET_ALL_PRODUCTS_CURSOR_API_RED.md`

작업:

- repository method 이름을 실제 코드와 맞춘다.
- size 검증 책임 위치를 controller validation으로 갱신한다.

---

## 5. 이번 리팩터링에서 바로 하지 않을 항목

다음 항목은 이번 리팩터링에서 제외하는 것을 추천한다.

- repository native query 분리
- projection 패키지 이동
- enum 변환 실패 정책 추가
- custom exception 추가
- cursor validation 추가
- MySQL 통합 테스트 추가

이유:

- 현재 테스트가 요구하지 않는다.
- 동작 변경 가능성이 있다.
- `docs/TDD/refactor.md`의 "한 번에 하나씩", "확장성 위한 구조 추가 금지" 원칙과 충돌할 수 있다.

---

## 6. 확인 필요 사항

리팩터링 착수 전 확인이 필요하다.

1. 테스트 코드 수정 금지 원칙을 유지한 상태에서 service size 검증 테스트를 어떻게 처리할지 결정해야 한다.
2. DTO factory 이동은 테스트 수정 없이 바로 진행해도 되는지 확인해야 한다.
3. `ProductCursorResponse.from(rows, size)`까지 포함할지, `ProductSummaryResponse`와 `ProductImageResponse` factory만 먼저 만들지 결정해야 한다.
4. 문서의 repository method 이름 불일치를 이번 리팩터링 문서 정리 범위에 포함할지 결정해야 한다.
