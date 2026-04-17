# DataImportService Refactor ADR

**작성일**: 2026-04-16  
**상태**: 승인됨  
**대상**: `DataImportService` 6차 리팩토링

---

## Context

5차 리팩토링 이후 `DataImportService`는 JSON 파싱이 공용 계층으로 이동된 상태다.

남아 있는 문제는 다음과 같다.

- `DataImportProductAssembler` 안에 남아 있는 값 추출/기본값 처리 함수들은 공용 재사용 코드에 가깝다.
- `text`, `isBlank`, `decimal`, `integer`, `longValue`, `details`는 다른 JSON 도메인에서도 그대로 쓰일 가능성이 높다.
- 이런 유틸이 assembler 내부에 있으면 재사용성과 테스트 독립성이 떨어진다.

---

## Decision

6차 리팩토링에서는 JSON 값 추출과 기본값 처리 책임을 `common/json` 계층으로 옮긴다.

- `JsonNodeValues`가 `text`, `isBlank`, `decimal`, `integer`, `longValue`, `details`를 제공한다.
- `DataImportProductAssembler`는 JSON 값을 직접 해석하지 않고 공용 유틸을 호출한다.
- 공용 JSON 계층은 파싱과 값 추출을 함께 책임진다.
- 다른 도메인에서도 같은 처리 규칙을 재사용할 수 있다.

---

## Rationale

이 변경은 동작을 바꾸지 않으면서도, JSON 값 추출 로직을 재사용 가능한 공용 계층으로 이동시킨다.

- 테스트 결과는 그대로 유지된다.
- 여러 도메인에서 같은 JSON 값 추출 방식을 재사용할 수 있다.
- 특정 도메인에만 속한 값 추출 코드가 공용 계층으로 정리된다.
- 서비스와 assembler는 JSON을 직접 해석하지 않고 공용 도구를 호출한다.

---

## Consequences

### Positive

- JSON 값 추출 기능이 공용화된다.
- 다른 도메인에서도 같은 값 추출 규칙을 재사용할 수 있다.
- assembler 내부의 변환 헬퍼가 사라진다.

### Negative

- 공용 계층이 커진다.
- 도메인별 특수한 파싱 규칙은 별도 확장이 필요할 수 있다.
- persistence와 duplicate check는 여전히 서비스에 남아 있어 완전한 도메인 주도 모델은 아니다.

---

## Verification

6차 변경 후 `./gradlew test`를 실행해 기존 데이터 적재 테스트가 모두 GREEN인지 확인한다.
