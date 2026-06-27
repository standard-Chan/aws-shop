# Product Elasticsearch Search 현재 상태

## 목적

이 문서는 이후 작업자가 상품 Elasticsearch 검색 구현 상태를 빠르게 파악하기 위한 진행 상태 문서다.
상세 설계 의사결정은 `docs/product/es-search/PLAN.md`와 `docs/product/es-search/API_SPLIT_ADR.md`를 함께 본다.

## 현재 완료된 범위

- 기존 MySQL LIKE 검색 API `GET /api/products/keyword`는 유지했다.
- 신규 Elasticsearch 검색 API `GET /api/products/search`를 추가했다.
- 관리자 재색인 API `POST /api/admin/products/search/reindex`를 추가했다.
- ES Java API Client 설정을 추가했다.
- MySQL product 데이터를 source of truth로 보고 ES index를 재색인하는 흐름을 구현했다.
- 검색 응답은 ES 전용 DTO를 사용한다.
- ES cursor는 기존 `cursorId`가 아니라 opaque `cursor` token을 사용한다.
- ES 관련 예외는 `product/exception/search` 아래 전용 예외 계층으로 분리했다.
- title 검색용 analyzer에 `lowercase`, `asciifolding`을 적용했다.
- 수동 호출용 HTTP 예시는 `src/test/http/products/product-search-api.http`에 추가했다.

## 주요 엔드포인트

### 상품 검색

```http
GET /api/products/search?keyword=wire&size=20&sort=averageRating&order=desc&cursor=...
```

- `keyword`: 필수
- `size`: 선택, 기본 `20`, 범위 `1..100`
- `sort`: 선택, `averageRating`, `ratingNumber`, `price`
- `order`: 선택, `asc`, `desc`
- `cursor`: 선택, ES `search_after` 기반 opaque cursor

blank keyword는 예외가 아니라 빈 응답을 반환한다.
잘못된 cursor는 `400 Bad Request`다.
ES 검색 실패는 MySQL LIKE fallback 없이 ES 검색 실패로 드러낸다.

### 재색인

```http
POST /api/admin/products/search/reindex?pageSize=500
```

- `pageSize`: 선택, 기본 `500`, 범위 `1..5000`
- MySQL 상품을 `id ASC` cursor 기준으로 page 단위 조회한다.
- 조회한 상품을 ES document로 변환한 뒤 Bulk API로 색인한다.

## 패키지 구조

```text
src/main/java/jeong/awsshop/product/service/search/
  ProductSearchService.java
  ProductSearchReindexService.java
  criteria/
    ProductSearchDirection.java
    ProductSearchSort.java
  cursor/
    ProductSearchCursor.java
    ProductSearchCursorCodec.java
  document/
    ProductSearchDocument.java
    ProductSearchImageDocument.java
  dto/
    ProductSearchImageResponse.java
    ProductSearchItemResponse.java
    ProductSearchReindexResponse.java
    ProductSearchResponse.java

src/main/java/jeong/awsshop/product/exception/search/
  ProductSearchException.java
  ProductSearchInvalidCursorException.java
  ProductSearchCursorEncodeException.java
  ProductSearchQueryException.java
  ProductSearchIndexPreparationException.java
  ProductSearchReindexException.java
```

핵심 비즈니스 로직은 `ProductSearchService`, `ProductSearchReindexService`다.
나머지 하위 패키지는 cursor, ES document, 정렬 기준, API DTO처럼 부수 모델을 분리하기 위한 구조다.

## ES document와 index mapping

ES 저장 포맷은 `ProductSearchDocument`다.

주요 필드:

- `id`
- `parentAsin`
- `title`
- `mainCategory`
- `averageRating`
- `ratingNumber`
- `price`
- `store`
- `image`

index 생성은 `ProductSearchReindexService.ensureIndex()`에서 수행한다.
`title` 필드는 `product_title_analyzer`를 사용한다.

현재 analyzer:

- tokenizer: `standard`
- filter: `lowercase`, `asciifolding`

주의: analyzer와 mapping은 index 생성 시점에 반영된다.
이미 생성된 index가 있으면 삭제 후 재색인해야 새 analyzer가 적용된다.

## 재색인 조회 쿼리 의도

`ProductRepository.findProductSearchReindexPage`는 `FROM` 절 서브쿼리에서 product page를 먼저 확정한 뒤 바깥 쿼리에서 대표 이미지를 붙인다.

의도:

- 재색인 batch 경계를 product row 기준으로 고정한다.
- `product_images`는 1:N 관계이므로 join row가 page 경계에 영향을 주는 실수를 피한다.
- 안쪽 쿼리는 "이번 batch 상품 집합 결정"을 담당한다.
- 바깥 쿼리는 "대표 이미지 보강"을 담당한다.

자세한 의도는 `API_SPLIT_ADR.md`의 "재색인 조회 쿼리에서 product page를 서브쿼리로 먼저 확정하는 이유"를 본다.

## 설정

`application.yml`:

```yaml
app:
  product-search:
    elasticsearch:
      enabled: ${PRODUCT_SEARCH_ES_ENABLED:true}
      uris: ${PRODUCT_SEARCH_ES_URIS:http://localhost:19200}
      index-name: ${PRODUCT_SEARCH_ES_INDEX:products-search}
```

`compose.yaml`에는 product search용 Elasticsearch 단일 노드가 추가되어 있다.

기본 포트:

- host: `19200`
- container: `9200`

## 예외 정책

상위 예외는 `ProductSearchException`이다.
모든 상품 ES 검색 예외 메시지는 `[Product Elasticsearch 오류]:` prefix를 갖는다.

예외 구분:

- cursor decode/validate 실패: `ProductSearchInvalidCursorException`
- cursor encode 실패: `ProductSearchCursorEncodeException`
- ES search API 실패: `ProductSearchQueryException`
- index 생성/확인 실패: `ProductSearchIndexPreparationException`
- bulk 재색인 실패: `ProductSearchReindexException`

## 검증 상태

최근 구현 검증:

```bash
./gradlew :compileJava
./gradlew :test
```

두 명령 모두 성공했다.

주의: 루트 `./gradlew test`는 멀티 프로젝트 전체를 실행하면서 `event-pipeline:hadoop-consumer` 쪽 기존 컴파일 문제에 막힌 적이 있다.
product 검색 변경 검증에는 루트 프로젝트 `:test`를 사용했다.

## 수동 테스트 순서

1. Elasticsearch를 실행한다.
2. Spring Boot 애플리케이션을 실행한다.
3. 재색인을 호출한다.
4. 검색 API를 호출한다.

요청 예시는 아래 파일을 사용한다.

```text
src/test/http/products/product-search-api.http
```

## 남은 작업 후보

- ES 통합 테스트를 Testcontainers 또는 별도 profile로 추가한다.
- 기존 index가 있을 때 mapping/analyzer 변경을 감지하고 운영자가 알 수 있게 한다.
- 재색인 실패 item의 상세 원인을 응답 또는 로그로 더 구체화한다.
- index alias 기반 zero-downtime reindex 전략을 검토한다.
- title 외 필드 검색, autocomplete, synonym, typo tolerance는 아직 비범위다.
- 관리자 재색인 API 보호 정책을 추가해야 한다.

## 관련 커밋

- `6e7be21 feat: 상품 Elasticsearch 검색 API 추가`
- `1f9e22f docs: 상품 검색 재색인 쿼리 구조 의도 기록`
- `d80cabb docs: 상품 검색 HTTP 테스트 예시 추가`
- `91d691e refactor: 상품 ES 검색 예외 계층 추가`
- `6a7f20d feat: 상품 ES 검색 asciifolding analyzer 추가`
