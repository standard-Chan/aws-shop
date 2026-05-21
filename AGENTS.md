# AGENTS.md

이 파일은 이 저장소에서 작업할 때 가장 먼저 읽는 개요 문서다. 세부 구현보다 프로젝트의 큰 구조와 작업 규칙을 빠르게 파악하는 용도다.

## 프로젝트 개요
- Spring Boot 3.2 / Java 21 기반의 커머스 서비스다.
- Amazon Reviews/products 대규모 데이터를 활용한 조회, 상세 조회, 리뷰, 데이터 적재 기능이 중심이다.
- DB는 MySQL을 전제로 하고, 테스트는 H2를 사용한다.

## 전체 구조
- `src/main/java/jeong/awsshop`: 애플리케이션 루트 패키지다.
- `common`: 공통 유틸, JSON 파서, 예외 처리, ID 생성 같은 공유 로직이 있다.
- `product`: 상품 조회, 상세 조회, 카테고리 조회, 데이터 적재 관련 기능이 모여 있다.
- `review`: 리뷰 bulk upload 및 저장 관련 기능이 있다.
- `user`: 사용자 관련 영역의 확장용 패키지로 보인다.

## 주요 레이어
- `controller`: HTTP API 진입점이다.
- `service`: 비즈니스 로직과 오케스트레이션을 담당한다.
- `repository`: JPA 및 조회 쿼리 계층이다.
- `domain`: 엔티티와 핵심 도메인 모델이 있다.
- `dto`: 요청/응답 전송 객체가 있다.
- `exception`: 기능별 예외와 전역 예외 처리기가 있다.

## 테스트 구조
- `src/test/java` 아래에 기능별 테스트가 정리되어 있다.
- `controller`, `service`, `repository` 단위로 나뉘어 있다.
- `src/test/http`에는 API 호출 예시가 있다.

## 문서 구조
- `docs/TDD`: RED, GREEN, REFACTOR 흐름과 관련된 작업 문서다.
- `docs/product`: 상품 API와 데이터 파이프라인 문서가 있다.
- `docs/review`: 리뷰 데이터 파이프라인 문서가 있다.
- `docs/design`: 설계 노트와 작업 기준이 있다.
- `docs/exec`: 실행 관련 설명이 있다.

## 작업 원칙
- 각 작업 도메인의 대상의 docs/도메인대상/도메인.md 문서를 읽는다. 만약 존재하지 않는다면, 비어있는 파일로 만들어둔다. 예를 들어, product 도메인을 작업 할때에는 `docs/product/PRODUCT.md`를 먼저 본다.
- 먼저 해당 기능의 `controller` / `service` / `repository` / `test` 위치를 확인한다.

## 실행 기준
- 일반 검증은 `./gradlew test`를 우선 사용한다.
- 설정은 `src/main/resources/application*.yml`과 `src/test/resources/application-test.yml`을 확인한다.
- 로컬 실행 관련 정보는 `compose.yaml`과 `README.md`를 함께 본다.

## 주의 사항
- 이 프로젝트는 데이터 적재와 조회 로직이 분리되어 있으므로, 한쪽 변경이 다른 쪽에 미치는 영향을 확인해야 한다.
- JSON 파싱, 커서 기반 조회, bulk insert 관련 변경은 회귀가 생기기 쉬우므로 테스트를 먼저 본다.
- 기존 문서가 있는 영역은 문서의 용어와 실제 코드 구조를 맞춰서 작업한다.
