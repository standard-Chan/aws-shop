# DataImportService Refactor ADR

**작성일**: 2026-04-16  
**상태**: 승인됨  
**대상**: `DataImportService` 4차 리팩토링

---

## Context

3차 리팩토링 이후 `DataImportService`는 JSON 파싱과 Product 조립 책임이 분리된 상태다.

남아 있는 문제는 다음과 같다.

- child entity 생성 규칙이 child entity 자체에 있지 않다.
- `Product`가 child를 컬렉션에 추가하지만, child가 자기 생성 규칙을 모른다.
- 객체는 아직 충분히 자기 생성 책임을 갖고 있지 않다.

---

## Decision

4차 리팩토링에서는 child entity 생성 책임을 child entity 자체로 옮긴다.

- `ProductFeature`, `ProductDescription`, `ProductCategory`, `ProductImage`, `ProductVideo`, `ProductBoughtTogether`가 각각 자기 생성 팩토리를 가진다.
- `DataImportProductAssembler`는 JSON 값을 읽고 child entity 팩토리를 호출해 `Product`에 추가만 한다.
- `Product`는 aggregate root로서 컬렉션 관리만 담당한다.
- `DataImportService`는 여전히 파싱, 중복 검사, 저장 흐름만 orchestration 한다.

---

## Rationale

이 변경은 동작을 바꾸지 않으면서도, 생성 책임을 child entity로 이동시킨다.

- 테스트 결과는 그대로 유지된다.
- 서비스와 assembler는 흐름과 데이터 전달에 집중한다.
- child entity가 자기 생성 규칙을 알게 되어 책임이 더 응집된다.
- `Product`는 aggregate root로서 컬렉션을 관리한다.

---

## Consequences

### Positive

- child entity별 생성 규칙이 엔티티 내부에 모인다.
- assembler는 JSON 해석과 팩토리 호출만 담당한다.
- 도메인 객체의 역할이 더 분명해진다.

### Negative

- child entity에 static factory가 추가된다.
- entity 파일 수와 메서드 수가 늘어난다.
- persistence와 duplicate check는 여전히 서비스에 남아 있어 완전한 도메인 주도 모델은 아니다.

---

## Verification

4차 변경 후 `./gradlew test`를 실행해 기존 데이터 적재 테스트가 모두 GREEN인지 확인한다.
