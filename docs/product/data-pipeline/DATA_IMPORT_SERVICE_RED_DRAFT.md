# DataImportService RED 초안

**작성일**: 2026-04-16  
**대상**: JSON line 1건을 파싱하고, Product 및 하위 엔티티를 생성한 뒤 FK를 연결하는 단건 적재 서비스
**상태**: 테스트 케이스 초안

---

## 1. 기능 분석

### 1.1 핵심 기능

- JSON line 1건을 입력받는다.
- JSON을 파싱한다.
- `Product` 루트 엔티티를 생성한다.
- child entity를 생성한다.
- child entity의 `product` FK를 연결한다.
- 저장 후 `parentAsin`으로 조회 가능해야 한다.

### 1.2 부가 기능

- `main_category` 문자열을 `MainCategory` enum으로 정규화한다.
- 배열 필드의 null/빈 값은 안전하게 무시한다.
- `details`는 null이어도 저장 가능해야 한다.
- `bought_together`는 null일 수 있다.

### 1.3 실패/예외 기능

- `parent_asin`이 없으면 저장하지 않는다.
- `title`이 없으면 저장하지 않는다.
- JSON이 깨져 있으면 저장하지 않는다.
- `parentAsin` 중복은 저장 실패로 처리한다.

---

## 2. 테스트 케이스 목록

### 2.1 성공해야 할 사항

| 우선순위 | 테스트 케이스 | 기대 결과 |
|---|---|---|
| P1 | JSON line 1건을 저장한다 | `Product`가 저장되고 조회 가능하다 |
| P1 | 저장한 상품을 `parentAsin`으로 조회한다 | 동일한 `Product`가 반환된다 |
| P1 | `features`가 있으면 child entity가 생성된다 | `ProductFeature`가 생성된다 |
| P1 | `description`이 있으면 child entity가 생성된다 | `ProductDescription`이 생성된다 |
| P1 | `images`가 있으면 child entity가 생성된다 | `ProductImage`가 생성된다 |
| P1 | `videos`가 있으면 child entity가 생성된다 | `ProductVideo`가 생성된다 |
| P1 | `bought_together`가 있으면 child entity가 생성된다 | `ProductBoughtTogether`가 생성된다 |

### 2.2 실패해야 할 사항

| 우선순위 | 테스트 케이스 | 기대 결과 |
|---|---|---|
| P1 | `parent_asin`이 null이다 | 저장하지 않는다 |
| P1 | `title`이 null이다 | 저장하지 않는다 |
| P1 | JSON이 유효하지 않다 | 파싱 실패 처리된다 |
| P2 | `parentAsin`이 중복이다 | 저장 실패 또는 중복 처리된다 |

### 2.3 Edge Case

| 우선순위 | 테스트 케이스 | 기대 결과 |
|---|---|---|
| P1 | `features: null`이다 | 에러 없이 저장된다 |
| P1 | `features: []`이다 | 빈 child collection으로 저장된다 |
| P1 | `description: null`이다 | 에러 없이 저장된다 |
| P1 | `categories: null`이다 | 에러 없이 저장된다 |
| P1 | `images: null`이다 | 에러 없이 저장된다 |
| P1 | `videos: []`이다 | 에러 없이 저장된다 |
| P1 | `details: null`이다 | `Product.details`가 null로 저장된다 |
| P1 | `bought_together: null`이다 | child 생성 없이 저장된다 |
| P2 | 배열 안에 null/blank 요소가 있다 | 해당 요소는 스킵된다 |
| P2 | `bought_together.relatedProductId`가 null이다 | child 생성 없이 저장된다 |
| P2 | `images` 객체 내부 일부 필드가 null이다 | 에러 없이 저장된다 |
| P2 | `videos` 객체 내부 일부 필드가 null이다 | 에러 없이 저장된다 |
| P2 | `main_category`가 `"Handmade"`이다 | enum 정규화 규칙에 따라 저장된다 |

### 2.4 예외 상황

| 우선순위 | 테스트 케이스 | 기대 결과 |
|---|---|---|
| P1 | child entity의 FK가 비어 있다 | 저장 시 예외가 나지 않도록 차단해야 한다 |
| P1 | `relatedProductId`가 null인 `ProductBoughtTogether`를 만든다 | 생성하지 않는다 |
| P2 | `feature`가 null인 `ProductFeature`를 만든다 | 생성하지 않는다 |
| P2 | `description`이 null인 `ProductDescription`를 만든다 | 생성하지 않는다 |

---

## 3. RED 우선순위

1. JSON line 1건 저장
2. `parentAsin` 조회
3. child FK 연결 검증
4. null/blank 안전 처리
5. JSON 파싱 실패 처리
6. 중복 `parentAsin` 처리
7. `main_category` 정규화

---

## 4. 다음 확인 사항

- 이 목록에서 먼저 작성할 테스트만 선택해야 한다.
- 구현 전에 테스트 메서드명 규칙을 `should_[기대결과]_when_[조건]`으로 고정해야 한다.
- null 정책 중 `ratingNumber`는 현재 설계상 `null -> 0`으로 맞추는 것이 일관적이다.
