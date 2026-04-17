# DataImportService Refactor Plan

**작성일**: 2026-04-16  
**대상 코드**: [DataImportService.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/product/service/DataImportService.java)  
**목적**: `docs/TDD/refactor.md` 기준으로 현재 구현의 냄새를 먼저 정리하고, 다음 변경 대상을 명확히 한다.

---

## 1. 현재 코드에서 보이는 냄새

### 1.1 한 클래스에 책임이 너무 많다

`DataImportService`가 아래 책임을 모두 가지고 있다.

- JSON line 파싱
- 필수값 확인
- 중복 `parentAsin` 확인
- `Product` 생성
- child entity 생성
- FK 연결
- 저장 예외 래핑

이 구조는 흐름을 한 번에 읽기에는 편하지만, 수정 포인트가 늘어나면 서비스가 쉽게 비대해진다.

### 1.2 절차형 코드가 많다

`insert` 메서드는 다음 순서로 절차를 직접 수행한다.

1. 파싱
2. 검증
3. 루트 생성
4. child 생성
5. 저장

이 흐름 자체는 단순하지만, 현재는 `Service`가 모든 절차를 직접 수행하고 있다.  
리팩토링 기준상, 생성과 변경 책임은 Entity 또는 Entity 조립 로직 쪽으로 더 내려갈 수 있다.

### 1.3 동일한 패턴의 반복이 많다

`addFeatures`, `addDescriptions`, `addCategories`, `addImages`, `addVideos`, `addBoughtTogether`가 모두 비슷한 형태다.

- null/array 검사
- 아이템 순회
- 개별 child entity 생성
- `product.addX(...)`

이 반복은 읽는 데는 큰 문제가 없지만, 구조적으로는 중복이 많다.

### 1.4 JSON key 문자열이 서비스에 하드코딩되어 있다

다음 값들이 코드 곳곳에 직접 박혀 있다.

- `parent_asin`
- `title`
- `main_category`
- `average_rating`
- `rating_number`
- `features`
- `description`
- `categories`
- `images`
- `videos`
- `bought_together`
- `relatedProductId`
- `related_product_id`
- `relatedProductTitle`
- `relatedProductImageUrl`

이런 문자열은 한 번 바뀌면 추적이 어렵다.  
최소한 상수화하거나, 파서/매퍼 계층으로 빼는 편이 낫다.

### 1.5 예외 메시지가 하드코딩되어 있다

현재 저장 실패 메시지는 서비스 내부 문자열로 직접 작성되어 있다.

- `"[DataImport insert 실패]: ..."`

예외 메시지는 상수로 분리하는 편이 유지보수에 낫다.

### 1.6 if 분기가 계속 늘어날 가능성이 높다

현재는 null-safe 처리 때문에 분기가 많다.

- `node == null || !node.isArray()`
- `item == null || item.isNull()`
- `relatedProductIdNode == null || relatedProductIdNode.isNull()`

현재 테스트는 이 동작을 요구하지만, 향후 포맷 확장 시 분기 수가 더 늘어날 가능성이 높다.

### 1.7 JSON 파싱과 도메인 매핑이 같은 클래스에 섞여 있다

`ObjectMapper`로 트리를 읽는 일과, `Product` 및 child entity를 만드는 일이 같은 클래스 안에 있다.

이 둘은 변경 이유가 다르다.

- 파싱 변경은 입력 포맷이 바뀔 때 필요하다.
- 매핑 변경은 도메인 구조나 DB 저장 정책이 바뀔 때 필요하다.

같은 클래스에 두면 수정 이유가 섞인다.

### 1.8 `mainCategory` 매핑은 도메인 규칙이지만 서비스에 남아 있다

`MainCategory.fromDisplayName(...)`로 옮겨서 단순해졌지만,  
서비스가 여전히 `main_category` 입력값을 직접 해석하고 있다.

여기서 더 나아가면:

- 입력 파싱
- enum 매핑
- child 생성

이 셋을 분리할 수 있다.

### 1.9 `Product`와 child entity의 생성 책임이 서비스에 있다

서비스가 각 entity의 builder를 직접 호출하고 있다.

이 방식은 빠르게 만들기 좋지만, 도메인 생성 규칙이 커질수록 서비스가 비대해진다.  
특히 `ProductFeature`, `ProductDescription`, `ProductImage`, `ProductVideo`, `ProductBoughtTogether`는 생성 규칙이 각각 다를 수 있다.

### 1.10 입력 변환 정책이 코드에 숨어 있다

서비스는 단순히 값을 읽는 것처럼 보이지만, 실제로는 여러 변환 정책을 함께 가진다.

- JSON 파싱 실패 시 예외를 던지지 않고 조용히 종료한다.
- 숫자 필드는 실패 시 `null` 또는 `0`으로 대체한다.
- 문자열 필드는 blank면 `null`로 바꾼다.
- `details`는 JSON 객체를 문자열로 직렬화한다.
- `main_category`는 display name을 enum으로 정규화한다.
- `image`와 `video` 객체 내부 키도 문자열 리터럴로 직접 읽는다.

이런 정책은 서비스 로직에 흩어져 있으면 추적이 어렵다.  
파서 또는 매퍼 계층으로 모으고, 실패 정책은 문서와 코드에서 동일하게 맞춰야 한다.

---

## 2. 리팩토링 우선순위

### 2.1 1순위

- JSON 파싱 책임 분리
- 입력 필드 상수화
- 예외 메시지 상수화
- 입력 변환 정책 문서화 및 분리

### 2.2 2순위

- child entity 생성 메서드 정리
- 공통 반복 패턴 최소화
- 서비스의 절차형 흐름 단순화

### 2.3 3순위

- entity 내부로 상태 변경 책임 이동 가능성 검토
- 필요 시 entity 조립 전용 메서드 도입

---

## 3. 권장 리팩토링 방향

### 3.1 파싱과 매핑 분리

`DataImportService`는 흐름만 남기고, 아래 책임을 별도 클래스로 뺀다.

- JSON line 파싱
- field extraction
- child data mapping

JSON line 파싱은 이제 공용 `common/json` 계층의 `JsonTreeParser`가 담당한다.

### 3.2 상수 분리

입력 key, 예외 메시지, 특수 문자열을 상수로 분리한다.

현재 1차 적용 대상:

- `DataImportJsonKey`
- `DataImportErrorMessage`
- `ProductImage` / `ProductVideo` / `ProductBoughtTogether`의 내부 key 상수

DataImport 예외는 공통 부모 예외를 두고, 하위 예외들이 상속하도록 정리한다.

### 3.3 child 생성 책임 정리

각 child entity의 생성 로직을 한 곳에 모아 중복을 줄인다.

이때 child 생성 책임은 단순히 builder 호출을 모으는 수준이 아니라,

- `product` FK를 붙이는 책임
- null/blank 스킵 책임
- 인덱스 부여 책임

까지 함께 정리하는 방향이 낫다.

### 3.4 서비스는 orchestration만 유지

서비스는 다음 정도만 담당한다.

- 입력 받기
- 중복 확인
- 파싱 결과를 도메인 생성 로직에 전달
- 저장 호출

### 3.5 단순 변환 함수 분리 예정

`DataImportProductAssembler` 안에는 여전히 단순한 변환 헬퍼가 남아 있다.

- `decimal(...)`
- `integer(...)`
- `longValue(...)`
- `details(...)`
- `text(...)`
- `isBlank(...)`

이 함수들은 도메인 생성 규칙이라기보다, JSON 읽기와 기본값 처리에 가까우므로 `common/json` 하위의 공용 변환 도구로 분리하는 것을 다음 단계로 둔다.

분리 기준은 다음과 같다.

- 데이터 변환 규칙이 재사용 가능해야 한다.
- entity 생성 책임과 무관해야 한다.
- assembler 안에서 직접적인 도메인 의미를 가지지 않아야 한다.

---

## 4. 진행 체크리스트

- [x] JSON 파싱 책임을 `common/json`의 `JsonTreeParser`로 분리했다.
- [x] `DataImportJsonKey`를 `service/dataimport` 패키지로 이동했다.
- [x] `DataImportService`는 orchestration만 담당하도록 줄였다.
- [x] `ProductFeature`, `ProductDescription`, `ProductCategory`, `ProductImage`, `ProductVideo`, `ProductBoughtTogether`에 자기 생성 팩토리를 추가했다.
- [x] child entity 생성 책임을 `Product` 외부에서 각 엔티티 쪽으로 옮겼다.
- [x] `DataImportProductAssembler`는 JSON 해석과 팩토리 호출만 담당하도록 정리했다.
- [x] `DataImportProductAssembler`의 단순 변환 함수(`decimal`, `integer`, `longValue`, `details`, `text`, `isBlank`)를 `common/json`의 `JsonNodeValues`로 분리했다.
- [ ] `JsonNodeValues`를 다른 JSON 적재 도메인에도 실제 적용한다.
- [x] DataImport 예외 계층을 공통 부모 예외 기준으로 정리했다.

---

## 5. 변경하지 말아야 할 것

- 테스트 코드는 수정하지 않는다.
- 현재 GREEN 동작은 바꾸지 않는다.
- 새로운 추상화는 필요할 때만 만든다.
- 한 번에 여러 방향을 동시에 바꾸지 않는다.

---

## 6. 다음 변경 제안

1. `JsonNodeValues`의 적용 범위를 다른 JSON 적재 도메인으로 확장
2. JSON key 상수화 범위 점검
3. 필요 시 assembler 내부 중복 제거
4. 서비스 레이어 추가 정리
