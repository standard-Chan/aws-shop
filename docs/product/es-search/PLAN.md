# Product Elasticsearch Search Plan

## 1. 목적

기존 `GET /api/products/keyword`는 MySQL `LIKE %keyword%` 기반으로 `product.title`을 검색한다.
대량 상품 데이터에서 keyword 검색 부하테스트 시 DB 부하가 커질 수 있으므로, Elasticsearch 기반 검색 API를 별도로 추가한다.

이번 계획의 목표는 검색 튜닝이 아니라 ES를 애플리케이션에 안정적으로 연결하고, 기본 검색 API와 색인 경로를 만드는 것이다.

## 2. 핵심 결정

- 기존 `GET /api/products/keyword`는 MySQL LIKE 기반 API로 유지한다.
- 신규 ES 검색 API는 `GET /api/products/search`로 분리한다.
- MySQL LIKE 검색과 ES 검색은 공통 interface로 추상화하지 않는다.
- ES 검색은 별도 service, adapter, request/response DTO를 가진다.
- ES 장애 시 `/api/products/search` 내부에서 MySQL LIKE fallback을 하지 않는다.
- 기존 LIKE API는 회귀 비교와 수동 대체 경로로 남긴다.
- ES client는 Elastic Java API Client를 사용한다.
- MySQL은 source of truth이고, ES는 검색용 read model로 본다.
- ES 색인은 관리 API를 통한 전체 재색인 방식으로 먼저 구현한다.

## 3. API 설계

### 3.1 기존 MySQL LIKE API

```http
GET /api/products/keyword?keyword=wire&size=20&sort=averageRating&order=desc&cursorId=...
```

기존 API는 현재 계약을 유지한다.

- 요청 parameter: `keyword`, `size`, `cursorId`, `sort`, `order`
- 응답: `ProductCategoryCursorResponse`
- 검색 의미: `title`에 대한 대소문자 무시 contains 검색
- pagination: 기존 `cursorId` 기반 cursor

### 3.2 신규 ES API

```http
GET /api/products/search?keyword=wire&size=20&sort=averageRating&order=desc&cursor=...
```

초기 요청 parameter는 다음으로 둔다.

| parameter | required | default | 설명 |
| --- | --- | --- | --- |
| `keyword` | Y | 없음 | ES에서 검색할 keyword |
| `size` | N | `20` | 응답 상품 수, 기존 API와 같이 `1..100` |
| `sort` | N | `averageRating` | `averageRating`, `ratingNumber`, `price` |
| `order` | N | `desc` | `asc` 또는 `desc` |
| `cursor` | N | 없음 | ES 전용 opaque cursor token |

초기 응답은 ES 전용 DTO를 사용한다.

```json
{
  "products": [
    {
      "id": "323413208910009350",
      "parentAsin": "B000000001",
      "title": "Silver Wire Basket",
      "mainCategory": "Handmade",
      "averageRating": 4.8,
      "ratingNumber": 50,
      "price": 19.99,
      "store": "Fixture Store",
      "image": {
        "variant": "MAIN",
        "thumb": "https://example.com/thumb.jpg",
        "large": "https://example.com/large.jpg",
        "hiRes": "https://example.com/hi-res.jpg"
      },
      "score": 12.34,
      "highlight": {
        "title": ["Silver <em>Wire</em> Basket"]
      }
    }
  ],
  "nextCursor": "opaque-search-after-token",
  "hasNext": true
}
```

응답의 모든 Long id는 기존 product 문서 원칙에 맞춰 문자열로 내려준다.

## 4. 검색 정책

초기 ES 검색은 `title` 검색만 다룬다.

- 기존 LIKE와 결과가 완전히 같을 필요는 없다.
- ES analyzer 기반 token 검색을 허용한다.
- `_score`와 title `highlight`를 응답에 포함한다.
- `sort` 요청이 없으면 기존 keyword API와 같이 `averageRating`을 기본 정렬로 둔다.
- 기존 정렬 비교를 위해 `averageRating`, `ratingNumber`, `price` 정렬을 지원한다.
- 동일 정렬값에서는 `id ASC`를 tie-breaker로 둔다.
- 정렬 기준값이 null인 상품은 기존 API와 같이 해당 정렬 결과에서 제외한다.

초기 범위에서 제외하는 검색 기능은 다음과 같다.

- ngram analyzer
- 형태소 분석
- 동의어 사전
- 오타 보정
- 자동완성
- relevance score 튜닝
- boost 정책
- 다중 필드 검색

## 5. Cursor 정책

ES API는 기존 `cursorId`를 재사용하지 않고 ES 전용 `cursor`를 사용한다.

- cursor는 클라이언트가 해석하지 않는 opaque string token이다.
- token 내부에는 ES `search_after` 재요청에 필요한 정렬값을 담는다.
- token은 서버에서 생성하고 서버에서만 해석한다.
- token 포맷은 public contract가 아니므로 이후 변경할 수 있다.
- 잘못된 token, 만료된 token, 현재 sort/order와 맞지 않는 token은 `400 Bad Request`로 처리한다.

초기 구현에서는 cursor token에 최소한 아래 값을 담는 방향으로 설계한다.

- 정렬 기준
- 정렬 방향
- 마지막 상품 id
- 마지막 상품의 정렬값

## 6. ES Document 설계

ES document는 상품 요약 응답과 검색/정렬에 필요한 값을 denormalize해서 가진다.

초기 document 필드:

| field | 용도 |
| --- | --- |
| `id` | 상품 id, keyword 검색 tie-breaker, 응답 |
| `parentAsin` | 응답 |
| `title` | 검색, highlight, 응답 |
| `mainCategory` | 응답, 후속 필터 후보 |
| `averageRating` | 정렬, 응답 |
| `ratingNumber` | 정렬, 응답 |
| `price` | 정렬, 응답 |
| `store` | 응답 |
| `image.variant` | 대표 이미지 응답 |
| `image.thumb` | 대표 이미지 응답 |
| `image.large` | 대표 이미지 응답 |
| `image.hiRes` | 대표 이미지 응답 |

대표 이미지는 기존 목록 조회 정책과 맞춘다.

- `variant = MAIN` 이미지가 있으면 우선 사용한다.
- `MAIN`이 없으면 `product_images.id ASC` 첫 이미지를 사용한다.
- 이미지가 없으면 `image = null`로 색인한다.

## 7. 인프라 설정

### 7.1 Gradle

ES Java API Client를 사용할 수 있도록 의존성을 추가한다.
Spring Boot 3.2.5와 충돌하지 않는 버전을 선택한다.

후보:

- `co.elastic.clients:elasticsearch-java`
- JSON mapper 연동에 필요한 Jackson 지원 의존성

### 7.2 Application 설정

`application.yml` 계열에 product ES 검색 설정을 둔다.

```yaml
app:
  product-search:
    elasticsearch:
      enabled: ${PRODUCT_SEARCH_ES_ENABLED:true}
      uris: ${PRODUCT_SEARCH_ES_URIS:http://localhost:19200}
      index-name: ${PRODUCT_SEARCH_ES_INDEX:products-search}
```

테스트 profile에서는 ES 통합 테스트를 별도 profile이나 Testcontainers로 분리한다.

### 7.3 Docker Compose

메인 `compose.yaml`에 product search용 Elasticsearch 단일 노드를 추가한다.

기본값은 기존 `event-pipeline`의 ES 설정과 맞춘다.

- image: `docker.elastic.co/elasticsearch/elasticsearch:8.13.4`
- `discovery.type=single-node`
- `xpack.security.enabled=false`
- `ES_JAVA_OPTS=-Xms512m -Xmx512m`
- host port는 기존 event-pipeline ES의 `19200`과 충돌하지 않게 정한다.

## 8. 재색인 계획

초기 색인은 관리 API로 전체 재색인을 실행한다.

예상 endpoint:

```http
POST /api/admin/products/search/reindex
```

재색인 흐름:

1. 관리 API가 재색인 service를 호출한다.
2. service가 MySQL product와 대표 이미지를 page 단위로 조회한다.
3. 조회 결과를 ES document로 변환한다.
4. ES Bulk API로 색인한다.
5. 처리 건수, 실패 건수, 소요 시간을 응답 또는 로그로 남긴다.

초기 구현에서는 증분 동기화와 실시간 dual-write를 제외한다.
상품 데이터가 바뀌면 관리 API로 전체 재색인을 다시 수행한다.

## 9. 구현 순서

1. `docs/product/es-search/API_SPLIT_ADR.md`에 API 분리 결정을 남긴다.
2. Gradle 의존성과 ES 설정 properties를 추가한다.
3. `compose.yaml`에 product search용 Elasticsearch를 추가한다.
4. ES client configuration을 추가한다.
5. ES document DTO와 검색 response DTO를 만든다.
6. `ProductSearchController` 또는 `ProductController`의 `/search` endpoint를 추가한다.
7. ES 검색 service를 구현한다.
8. cursor token encoder/decoder를 구현한다.
9. MySQL -> ES 재색인 repository/service/admin API를 구현한다.
10. 테스트를 작성하고 `./gradlew test`로 회귀를 확인한다.

## 10. 테스트 계획

### Controller

- `/api/products/search`가 `keyword`, `size`, `sort`, `order`, `cursor`를 ES service에 전달한다.
- `keyword` 누락은 `400 Bad Request`다.
- `size`는 기존 API와 같이 `1..100` 범위만 허용한다.
- ES 응답 DTO에 `score`, `highlight`, `nextCursor`, `hasNext`가 포함된다.

### Service

- blank keyword면 빈 응답을 반환한다.
- sort가 없으면 `averageRating`을 사용한다.
- `order=asc`면 오름차순 정렬 요청을 만든다.
- cursor token이 있으면 `search_after` 조건을 만든다.
- 잘못된 cursor token은 `400 Bad Request`다.
- ES client 예외는 LIKE fallback 없이 ES 검색 예외로 전파한다.

### ES Adapter Integration

- title token 검색 결과가 반환된다.
- score가 응답에 매핑된다.
- title highlight가 응답에 매핑된다.
- `averageRating`, `ratingNumber`, `price` 정렬이 동작한다.
- `size + 1` 조회로 `hasNext`와 `nextCursor`를 만든다.

### Reindex

- MySQL 상품과 대표 이미지가 ES document로 변환된다.
- Bulk API 요청이 page 단위로 생성된다.
- 실패 row 또는 실패 batch를 로그와 결과에 남긴다.

## 11. 비범위

- 기존 `/api/products/keyword` 제거
- MySQL LIKE 검색과 ES 검색의 공통 interface 추상화
- ES 장애 시 서버 내부 LIKE fallback
- 검색 결과의 LIKE 완전 호환
- analyzer 튜닝
- 운영용 blue/green index alias 전환
- 상품 변경 이벤트 기반 증분 색인
- 부하테스트 결과 문서화

