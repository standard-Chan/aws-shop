# DataImportService Refactor ADR

**작성일**: 2026-04-16  
**상태**: 승인됨  
**대상**: `DataImportService` 7차 리팩토링

---

## Context

6차 리팩토링 이후 `DataImportService`는 JSON 값 추출이 공용 계층으로 이동된 상태다.

남아 있는 문제는 다음과 같다.

- DataImport 예외들이 공통 부모 예외 없이 제각각 정의되어 있다.
- 예외 메시지 규칙이 일관되지 않아 원인 파악이 어렵다.
- 예외 타입과 메시지 prefix를 한 번에 정리할 필요가 있다.

---

## Decision

7차 리팩토링에서는 DataImport 예외 계층을 공통 부모 예외를 중심으로 정리한다.

- `DataImportException`을 공통 부모로 둔다.
- `DataImportDuplicateParentAsinException`, `DataImportPersistenceException`, `DataImportRequiredFieldException`은 이 부모를 상속한다.
- 모든 DataImport 예외 메시지는 `[]` prefix와 부연설명을 포함한다.
- 메시지 포맷을 통해 장애 원인을 빠르게 식별할 수 있다.

---

## Rationale

이 변경은 동작을 바꾸지 않으면서도, DataImport 예외 체계를 일관되게 만든다.

- 테스트 결과는 그대로 유지된다.
- 예외 타입이 공통 부모 아래로 정리된다.
- 메시지 규칙이 통일된다.
- 로그와 테스트에서 원인을 식별하기 쉬워진다.

---

## Consequences

### Positive

- DataImport 예외 메시지가 일관된다.
- 공통 부모 예외를 기준으로 catch 범위를 넓히기 쉬워진다.
- 장애 원인 추적성이 높아진다.

### Negative

- 예외 계층이 추가된다.
- 메시지 규칙을 지키지 않으면 테스트가 실패할 수 있다.
- persistence와 duplicate check는 여전히 서비스에 남아 있어 완전한 도메인 주도 모델은 아니다.

---

## Verification

7차 변경 후 `./gradlew test`를 실행해 기존 데이터 적재 테스트가 모두 GREEN인지 확인한다.
