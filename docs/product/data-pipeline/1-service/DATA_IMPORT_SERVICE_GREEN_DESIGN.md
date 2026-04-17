# DataImportService GREEN 설계 문서

**작성일**: 2026-04-16  
**기준 테스트**: [DataImportServiceTest.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/test/java/jeong/awsshop/product/service/DataImportServiceTest.java)
**목적**: RED 테스트를 통과하는 최소 적재 구현 방향을 정리한다

---

## 1. 설계 원칙

이 문서는 `docs/TDD/green.md`의 원칙을 따른다.

- 이 테스트만 통과하는 최소 구현만 만든다.
- 확장성은 고려하지 않는다.
- 아직 테스트가 없는 기능은 구현하지 않는다.
- 메서드가 테스트 대상이 아니면 `UnsupportedOperationException`을 던질 수 있다.
- 서비스 흐름은 단순하게 유지한다.

---

## 2. 대상 API

### 2.1 서비스

- `DataImportService`

### 2.2 공개 메서드

- `insert(String jsonLine)`
- `findByParentAsin(String parentAsin)`

### 2.3 저장 대상

- `Product`
- `ProductFeature`
- `ProductDescription`
- `ProductCategory`
- `ProductImage`
- `ProductVideo`
- `ProductBoughtTogether`

---

## 3. 최소 책임

### 3.1 `insert`

`insert`는 JSON line 1개를 받아 다음을 수행한다.

1. JSON 문자열을 파싱한다.
2. 필수 필드를 확인한다.
3. `Product`를 만든다.
4. child entity를 만든다.
5. 각 child entity의 `product` FK를 같은 `Product`로 연결한다.
6. 저장한다.

### 3.2 `findByParentAsin`

`parentAsin`으로 `Product`를 조회한다.

- 존재하면 반환한다.
- 없으면 empty 결과를 반환한다.

---

## 4. 입력 처리 규칙

### 4.1 필수 필드

- `parent_asin`
- `title`
- `main_category`

### 4.2 null 정책

현재 테스트에 맞춰 다음처럼 처리한다.

- `parent_asin`이 null이면 저장하지 않는다.
- `parent_asin`이 이미 존재하면 중복 유니크 에러를 반환한다.
- `title`이 null이면 저장하지 않는다.
- `main_category`는 `"Handmade"`를 `HANDMADE_PRODUCTS`로 정규화한다.
- `rating_number`가 null이면 0으로 저장한다.
- `average_rating`이 null이면 null 그대로 저장한다.
- `price`는 null 그대로 저장한다.
- `store`는 null 그대로 저장한다.
- `details`는 null 그대로 저장한다.

### 4.3 배열 정책

- `features`, `description`, `categories`, `images`, `videos`가 null이어도 에러 없이 빈 컬렉션으로 처리한다.
- child entity 내부의 null/blank 요소는 생성하지 않는다.
- `bought_together`가 null이면 생성하지 않는다.

---

## 5. 저장 규칙

### 5.1 루트 엔티티

`Product` 생성 시 필요한 최소 필드:

- `id`
- `parentAsin`
- `title`
- `mainCategory`
- `averageRating`
- `ratingNumber`
- `price`
- `store`
- `details`

### 5.2 child entity 연결

생성 직후 `product` 필드에 같은 부모를 넣는다.

- `ProductFeature.product = product`
- `ProductDescription.product = product`
- `ProductCategory.product = product`
- `ProductImage.product = product`
- `ProductVideo.product = product`
- `ProductBoughtTogether.product = product`

### 5.3 저장 방식

단순한 구현을 위해 `Product` 저장만 수행하고 cascade로 child를 함께 저장한다.

---

## 6. 필요한 최소 컴포넌트

### 6.1 서비스 내부 또는 보조 메서드

- JSON 파서
- `Product` 생성 메서드
- child entity 생성 메서드
- null 스킵 메서드

### 6.2 저장소

- `ProductRepository`
- child repository들

테스트에서 count와 조회를 확인하므로, 최소한 저장과 조회가 가능해야 한다.

---

## 7. 테스트별 구현 포인트

### 7.1 정상 저장

- 유효한 JSON line을 넣으면 `Product` 1건과 child entity가 저장되어야 한다.

### 7.2 조회

- `parentAsin`으로 조회하면 저장된 상품이 반환되어야 한다.

### 7.3 null 배열

- 배열 필드가 null이어도 저장이 실패하면 안 된다.
- child entity는 생성하지 않는다.

### 7.4 필수값 누락

- `parent_asin` null이면 `DataImportRequiredFieldException`을 던진다.
- `title` null이면 `DataImportRequiredFieldException`을 던진다.

### 7.5 잘못된 JSON

- 파싱 실패 시 `DataImportParsingException`을 던진다.
- 예외 메시지에는 파싱 실패 원인을 포함한다.

### 7.6 중복 유니크 값

- `parentAsin`이 이미 존재하면 `DataImportDuplicateParentAsinException`을 던진다.
- 별도 upsert 처리 없이 중복 저장을 막는다.

---

## 8. 예외 정책

이 서비스는 현재 테스트 범위에서 발생 가능한 예외를 구체적인 타입으로 나눈다.

### 8.1 `DataImportParsingException`

- 발생 시점: JSON line 파싱 실패
- 원인: 잘못된 JSON 형식, 잘린 문자열, 필드 구문 오류
- 처리: 저장하지 않고 이 예외를 반환하거나 래핑해 던진다
- 비고: Jackson 예외를 직접 노출하지 않고 도메인 예외로 감싼다

### 8.2 `DataImportRequiredFieldException`

- 발생 시점: 필수 필드 누락 또는 blank 값
- 대상 필드: `parent_asin`, `title`
- 처리: 저장하지 않는다
- 비고: 예외 메시지에는 누락된 필드명을 포함한다

### 8.3 `DataImportDuplicateParentAsinException`

- 발생 시점: `parentAsin`이 이미 DB에 존재할 때
- 처리: 저장하지 않고 중복 에러를 반환한다
- 비고: 현재 RED 테스트의 중복 케이스는 이 예외로 수렴시킨다

### 8.4 `DataImportPersistenceException`

- 발생 시점: DB 저장 중 예상하지 못한 실패
- 원인: JPA 저장 오류, FK/UNIQUE 제약 위반, 기타 persistence 예외
- 처리: 저장을 중단하고 이 예외로 래핑한다
- 비고: 내부적으로는 Spring/JPA 예외를 직접 노출하지 않는다

### 8.5 예외가 아닌 처리

- `main_category` 매핑 실패: 예외 대신 `UNKNOWN`
- `rating_number` null: 예외 대신 0
- 배열 필드 null: 예외 대신 빈 컬렉션
- child 객체 내부 null 값: 예외 대신 스킵
- `bought_together` null: 예외 없이 스킵

---

## 9. 구현 순서

1. `insert` 메서드 시그니처 작성
2. JSON 파싱
3. 필수값 검증
4. `Product` 생성
5. child entity 생성
6. `Product` 저장
7. `findByParentAsin` 구현
8. 테스트 실행 후 필요한 최소 수정만 반영

---

## 10. 주의 사항

- `ingest`라는 이름은 사용하지 않는다.
- 테스트 클래스명은 phase suffix 없이 `DataImportServiceTest`로 통일한다.
- 테스트가 요구하지 않는 추가 기능은 넣지 않는다.
