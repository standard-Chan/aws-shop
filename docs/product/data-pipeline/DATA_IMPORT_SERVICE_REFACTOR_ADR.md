# DataImportService Refactor ADR

**작성일**: 2026-04-16  
**상태**: 승인됨  
**대상**: `DataImportService` 2차 리팩토링

---

## Context

1차 리팩토링 이후 `DataImportService`는 JSON 파싱만 별도 컴포넌트로 분리된 상태다.

남아 있는 문제는 다음과 같다.

- 필드 추출, 기본값 변환, main category 정규화, child entity 생성이 `DataImportService` 안에 남아 있다.
- `feature`, `description`, `category`, `image`, `video`, `bought_together` 조립 로직이 메서드별로 반복된다.
- `DataImportJsonKey`가 서비스 패키지에 있어, 적재 보조 타입들이 흩어져 있다.

---

## Decision

2차 리팩토링에서는 Product 조립 책임과 JSON key 상수를 `service/dataimport` 패키지로 옮긴다.

- `DataImportProductAssembler`가 `Product`와 child entity 조립을 담당한다.
- `DataImportProduct`는 `parentAsin`과 `Product`를 함께 전달하는 결과 객체로 사용한다.
- `DataImportJsonKey`는 `service/dataimport` 패키지로 이동한다.
- `DataImportService`는 파싱, 조립, 중복 검사, 저장 흐름만 orchestration 한다.
- `DataImportService` 안의 field extraction, child 생성 helper 메서드는 제거한다.

---

## Rationale

이 변경은 동작을 바꾸지 않으면서도, 서비스의 절차형 코드를 한 단계 더 줄인다.

- 테스트 결과는 그대로 유지된다.
- 서비스의 책임이 흐름 제어에 더 가까워진다.
- Product 조립 로직이 한 곳에 모여 추후 변경 지점이 명확해진다.
- JSON key 상수와 조립 로직을 함께 관리할 수 있다.

---

## Consequences

### Positive

- 필드 추출과 child 생성 책임이 서비스에서 제거된다.
- `feature`, `description`, `images`, `videos`, `bought_together`의 반복 코드를 한 컴포넌트에서 관리할 수 있다.
- 적재 보조 타입들이 같은 패키지에 모여 구조가 단순해진다.

### Negative

- 클래스와 결과 record가 추가된다.
- 아직 persistence와 duplicate check는 서비스에 남아 있어 완전한 분리는 아니다.

---

## Verification

2차 변경 후 `./gradlew test`를 실행해 기존 데이터 적재 테스트가 모두 GREEN인지 확인한다.
