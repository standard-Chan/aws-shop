# Category별 Product Cursor 조회 API 리팩터링 검토 문서

**작성일**: 2026-04-22  
**대상**: `GET /api/products/category` category cursor pagination API  
**참고 문서**: `docs/TDD/refactor.md`, `GET_PRODUCTS_BY_CATEGORY_CURSOR_API_DESIGN.md`, `GET_PRODUCTS_BY_CATEGORY_CURSOR_API_RED.md`  
**상태**: 코드 리팩터링 적용 완료, 큰 구조 변경 후보만 보류

---

## 0. 리팩터링 체크리스트

- [x] 2.1 `ProductReadService#getProductsByCategory`의 정렬 선택 boolean을 의도 있는 메서드로 분리
- [x] 2.2 cursor 검증의 조건 계산과 예외 발생 책임을 작은 메서드로 분리
- [x] 2.3 category 조회 실패 예외 메시지를 전용 예외 클래스로 분리
- [x] 2.7 category product rows 조회 로직을 의도 있는 메서드로 분리
- [x] 2.4 `ProductCategoryCursorResponse`의 cursor 생성 분기를 의도 있는 메서드 이름으로 정리
- [ ] 2.5 category native query의 중복 SQL 구조 정리
- [ ] 2.6 사용하지 않는 repository 테스트 helper 제거

---

## 1. 리팩터링 원칙

이번 문서는 `docs/TDD/refactor.md`의 1단계에 해당한다.  
따라서 테스트 코드와 구현 코드는 수정하지 않고, 냄새나는 부분과 리팩터링 후보만 정리한다.

리팩터링을 진행할 때 지켜야 할 기준은 다음과 같다.

- 테스트 코드는 사용자의 명시 요청 전까지 수정하지 않는다.
- 테스트는 계속 GREEN을 유지한다.
- 동작은 변경하지 않는다.
- 한 번에 하나의 변경만 적용한다.
- 요청하지 않은 인터페이스, 추상화, 확장 구조는 추가하지 않는다.
- Service는 흐름 제어에 집중한다.
- 예외 메시지는 의미 있는 prefix를 포함하고, 반복 문자열은 상수로 관리한다.

---

## 2. 발견한 리팩터링 후보

### 2.1 정렬 선택 boolean의 의도가 즉시 드러나지 않음

현재 코드:

```java
boolean averageRatingSort = averageRating || !ratingNumber;
```

문제:

- `averageRating || !ratingNumber`는 설계 규칙을 압축한 조건이다.
- "averageRating=true가 우선이고, 둘 다 false이면 averageRating이 기본값"이라는 정책이 코드 한 줄에 숨어 있다.
- 이후 repository 선택 삼항 연산자와 연결되면서 service 흐름을 읽기 위해 조건식을 다시 해석해야 한다.

리팩터링 방향:

- 정렬 정책을 private method로 분리한다.
- 메서드 이름에 정책을 드러낸다.
- 동작은 그대로 유지한다.

예상 코드 방향:

```java
private boolean shouldSortByAverageRating(boolean averageRating, boolean ratingNumber) {
    return averageRating || !ratingNumber;
}
```

우선순위:

- 높음

적용 결과:

- `shouldSortByAverageRating(boolean averageRating, boolean ratingNumber)`로 분리했다.
- `averageRating=true` 우선, `ratingNumber=false`일 때 averageRating 기본 정렬이라는 기존 정책은 유지했다.

### 2.2 cursor 검증 메서드가 여러 책임을 함께 가짐

현재 상태:

- `validateCursor`가 다음 책임을 모두 가진다.
  - 정렬 기준별 cursor 값 존재 여부 판단
  - cursor 조합 검증
  - 첫 페이지 요청 조기 반환
  - cursor id 존재 여부 검증
  - cursor category 일치 여부 검증
  - HTTP 예외 생성

문제:

- 검증 흐름 자체는 길지 않지만, 조건의 의미가 메서드 내부에 섞여 있다.
- `sortCursorExists`는 정렬 기준에 따라 의미가 달라지는 값인데 이름만으로 어떤 cursor인지 드러나지 않는다.
- cursor 조합 오류와 repository 기반 cursor 유효성 검증이 한 메서드에 공존한다.

리팩터링 방향:

- 조건 계산을 의도 있는 메서드로 분리한다.
- cursor 조합 검증과 repository 확인을 별도 메서드로 나눈다.
- 예외 타입과 메시지는 유지한다.

예상 코드 방향:

```java
private boolean hasSortCursorValue(
        BigDecimal cursorAverageRating,
        Integer cursorRatingNumber,
        boolean averageRatingSort
) {
    return averageRatingSort
            ? cursorAverageRating != null
            : cursorRatingNumber != null;
}
```

우선순위:

- 높음

적용 결과:

- `hasSortCursorValue`로 정렬 cursor 값 존재 여부 계산을 분리했다.
- `validateCursorPair`로 cursor id와 정렬 cursor 값의 조합 검증을 분리했다.
- `validateCursorProduct`로 repository 기반 cursor product 검증을 분리했다.

### 2.3 category 조회 실패 예외 메시지가 service에 하드코딩됨

현재 코드:

```java
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "[Category 조회 실패]: 알 수 없는 category입니다.");
```

문제:

- 같은 prefix인 `[Category 조회 실패]`가 여러 번 반복된다.
- 메시지 문구 변경 시 service 내부 여러 위치를 수정해야 한다.
- `docs/TDD/refactor.md`의 "예외 메시지 하드코딩 제거" 규칙과 맞지 않는다.

리팩터링 방향:

- `exception/` 디렉토리에 category 조회 전용 부모 예외 클래스를 추가한다.
- 부모 예외 클래스가 `ResponseStatusException`을 상속하도록 해 기존 HTTP 400 동작과 테스트 기대 타입을 유지한다.
- 실패 사유별 하위 예외 클래스를 추가한다.
- 반복 prefix는 부모 예외가 관리하고, 개별 사유 메시지는 하위 예외가 관리한다.
- service는 factory method가 아니라 구체 예외 클래스를 직접 throw한다.

예상 코드 방향:

```java
throw new UnknownProductCategoryException();
```

우선순위:

- 높음

적용 결과:

- `src/main/java/jeong/awsshop/product/exception/productread/ProductCategoryReadException.java`를 추가했다.
- service 내부의 `ResponseStatusException` 직접 생성과 하드코딩 메시지를 제거했다.
- `UnknownProductCategoryException`, `MissingCategoryCursorIdException`, `MissingCategorySortCursorException`, `ProductCategoryCursorNotFoundException`, `ProductCategoryCursorMismatchException`으로 실패 사유별 예외 타입을 분리했다.

### 2.4 `ProductCategoryCursorResponse`의 cursor 생성 분기가 정책을 직접 드러냄

현재 코드:

```java
private static CategoryCursor cursorFrom(ProductSummaryResponse product, boolean averageRatingSort) {
    if (averageRatingSort) {
        return new CategoryCursor(product.id(), product.averageRating(), null);
    }
    return new CategoryCursor(product.id(), null, product.ratingNumber());
}
```

문제:

- 분기 자체는 작지만, "정렬 기준에 맞는 cursor 필드만 채운다"는 정책이 메서드 안의 null 배치로 표현된다.
- 다음 cursor 생성 규칙을 읽으려면 `CategoryCursor` 생성자 인자 순서를 함께 알아야 한다.

리팩터링 방향:

- `CategoryCursor`에 정적 factory를 추가하거나, DTO 내부 private method 이름을 더 구체화한다.
- 새 추상화는 만들지 않는다.
- 응답 JSON 구조와 null 정책은 그대로 유지한다.

예상 코드 방향:

```java
public static CategoryCursor averageRatingCursor(ProductSummaryResponse product) {
    return new CategoryCursor(product.id(), product.averageRating(), null);
}

public static CategoryCursor ratingNumberCursor(ProductSummaryResponse product) {
    return new CategoryCursor(product.id(), null, product.ratingNumber());
}
```

우선순위:

- 중간

적용 결과:

- `CategoryCursor.averageRatingCursor(ProductSummaryResponse product)`를 추가했다.
- `CategoryCursor.ratingNumberCursor(ProductSummaryResponse product)`를 추가했다.
- `ProductCategoryCursorResponse`는 cursor 생성자 인자 순서 대신 정적 factory method 이름으로 cursor 정책을 표현한다.

### 2.5 category native query 두 개의 SQL 중복이 큼

현재 상태:

- `findCategoryProductSummariesOrderByAverageRating`
- `findCategoryProductSummariesOrderByRatingNumber`

두 query는 다음 구조가 거의 같다.

- product summary select
- category 필터
- 정렬 기준 null 제외
- cursor 조건
- 대표 image ranking subquery
- 최종 order by

문제:

- 대표 image 선택 SQL이 전체 목록 query와 category query에 반복된다.
- category query 두 개는 정렬 기준과 cursor 조건만 다르고 대부분 동일하다.
- 추후 대표 image 정책이 바뀌면 여러 query를 같이 수정해야 한다.

리팩터링 방향:

- 이번 단계에서는 바로 구조를 크게 바꾸지 않는다.
- custom repository, named query, SQL 상수 분리는 모두 변경 폭이 커질 수 있다.
- `docs/TDD/refactor.md`가 요청하지 않은 추상화 추가를 금지하므로, 사용자 확인 후 별도 단계에서 처리한다.
- 현재는 테스트 GREEN 유지와 좁은 리팩터링 범위를 우선해 보류한다.

우선순위:

- 낮음

### 2.6 repository 테스트의 `findByParentAsin` helper가 사용되지 않음

현재 코드:

```java
private ProductSummaryNativeProjection findByParentAsin(String parentAsin) {
    return productRepository.findProductSummaries(null, 20)
            .stream()
            .filter(row -> row.getParentAsin().equals(parentAsin))
            .findFirst()
            .orElseThrow();
}
```

문제:

- `ProductRepositoryFindCategoryProductSummariesTest` 안에서 호출되지 않는다.
- 사용하지 않는 helper는 테스트 의도를 흐린다.

리팩터링 방향:

- `docs/TDD/refactor.md`가 테스트 코드 수정 금지를 명시하므로 지금은 삭제하지 않는다.
- 사용자가 테스트 코드 정리를 허용하면 제거한다.
- 이번 작업에서는 테스트 코드를 절대 수정하지 않는 조건 때문에 보류한다.

우선순위:

- 낮음

### 2.7 category product rows 조회 로직이 직관적이지 않음

변경 전 코드:

```java
List<ProductSummaryNativeProjection> rows = averageRatingSort
        ? productRepository.findCategoryProductSummariesOrderByAverageRating(...)
        : productRepository.findCategoryProductSummariesOrderByRatingNumber(...);
```

문제:

- service의 주요 흐름 안에 repository 선택 삼항 연산자가 직접 들어 있어 조회 의도가 한눈에 들어오지 않는다.
- `rows`가 어떤 기준으로 조회되는지 이해하려면 `averageRatingSort`와 두 repository method 호출을 함께 읽어야 한다.
- `queryLimitForHasNext(size)`도 각 분기 내부에 섞여 있어 "category 상품 rows를 가져온다"는 흐름이 덜 명확하다.

리팩터링 방향:

- rows 조회를 `findCategoryProductSummaries` private method로 분리한다.
- service public method는 `category 파싱 -> 정렬 선택 -> cursor 검증 -> rows 조회 -> 응답 생성` 흐름만 드러나게 한다.
- repository method 선택은 private method 내부에서 처리한다.

적용 결과:

- `findCategoryProductSummaries(...)`를 추가했다.
- public service method에서 rows 조회 의도를 메서드 이름으로 읽을 수 있게 했다.
- 동작과 repository 호출 조건은 유지했다.

우선순위:

- 높음

---

## 3. 현재 구조에서 유지할 부분

다음 항목은 냄새로 보지 않고 현재 설계를 유지한다.

- `ProductController`가 category 문자열을 변환하지 않고 service에 그대로 전달하는 구조
- `size`를 controller Bean Validation으로 검증하는 구조
- `MainCategory.fromQueryParam`이 enum name과 하이픈 연결 표시값을 함께 처리하는 구조
- `ProductCategoryCursorResponse.from`이 `size + 1` 결과로 `hasNext`와 `nextCursor`를 조립하는 구조
- repository가 예외 처리를 하지 않고 조회 조건, 정렬, cursor 조건만 담당하는 구조

---

## 4. 권장 진행 순서

1. `ProductReadService`의 정렬 선택 조건을 `shouldSortByAverageRating`으로 분리한다. 완료
2. category 조회 실패 예외를 전용 예외 클래스로 분리한다. 완료
3. cursor 조합 검증과 cursor id 검증을 작은 private method로 나눈다. 완료
4. rows 조회 로직을 `findCategoryProductSummaries`로 분리한다. 완료
5. `CategoryCursor` 생성 factory 추가 여부를 확인한 뒤 DTO 내부에서 처리한다. 완료
6. SQL 중복 정리는 별도 리팩터링 작업으로 분리한다. 보류
7. 테스트 helper 제거는 테스트 코드 수정 허용 여부를 확인한 뒤 진행한다. 보류

---

## 5. 남은 리팩터링 검토 사항

이번 요청으로 service의 예외 생성, 정렬 선택, cursor 검증, rows 조회 흐름과 DTO cursor 생성 의도는 정리했다.

아직 남은 후보는 다음과 같다.

- 2.5 SQL 중복 정리
- 2.6 테스트 helper 제거

판단:

- 2.5는 변경 폭이 커질 수 있으므로 별도 작업으로 분리하는 편이 안전하다.
- 2.6은 테스트 코드 수정 금지 조건 때문에 이번 작업에서는 진행하지 않는다.
