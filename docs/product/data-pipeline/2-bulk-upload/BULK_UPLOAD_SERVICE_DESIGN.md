# Bulk Upload Service 설계 문서

**작성일**: 2026-04-17  
**대상**: AWS Shop 상품 JSONL bulk upload 파이프라인  
**범위**: streaming 방식으로 JSONL 데이터를 받아 line-by-line으로 파싱하고, 객체를 batch 크기 단위로 묶어 JDBC batch로 저장하는 bulk upload 설계

---

## 1. 목적

이 문서는 JSONL 데이터 수십만 건 이상을 메모리에 모두 올리지 않고, 스트리밍 방식으로 처리한 뒤 JDBC batch로 저장하는 bulk upload 흐름을 정의한다.

핵심 목표는 다음과 같다.

1. JSONL 입력을 streaming 방식으로 읽는다.
2. 각 line을 객체로 변환한다.
3. 변환된 객체를 batch size 단위로 묶는다.
4. JDBC batch로 한 번에 저장한다.
5. 실패한 데이터만 JSONL로 별도 저장해 재업로드할 수 있게 한다.
6. 대용량 입력에서도 메모리 사용량을 제한한다.

이 문서는 설계 문서이며, 구현은 포함하지 않는다.

---

## 2. 문제 정의

현재 `DataImportService`는 JSON line 1건을 받아 상품 1건을 저장하는 단건 적재 유스케이스다.

bulk upload는 이 단건 유스케이스를 반복 호출하는 방식이 아니라, JDBC batch 저장에 적합한 별도 적재 흐름을 제공해야 한다.

- 파일 전체를 `String`으로 읽지 않아야 한다.
- line 단위로 파싱해야 한다.
- line 단위로 객체를 생성해야 한다.
- 객체들을 batch size 단위로 묶어 저장해야 한다.
- 실패한 line만 별도 JSONL로 저장해야 한다.
- 업로드 진행 결과를 사용자에게 요약해서 보여줘야 한다.

---

## 3. 서비스 범위

### 3.1 입력

입력은 JSONL 데이터 스트림이다.

예시 입력 형태:

```text
{"main_category": "Handmade", "title": "Daisy Keychain Wristlet Gray Fabric Key fob Lanyard", "average_rating": 4.5, "rating_number": 12, "features": ["High Quality Fabrics", "Antique Brass Metallic Hardware", "1\" wide; Approx. 5-1/2\" loop opening", "Handmade in California"], "description": ["This charming Daisy Fabric Keychain wristlet features an opening that loops around your wrist allowing your hands to be free to carry other things! This sweet floral daisy key fob will be your little dose of joy, lifting your spirits each time you reach for your keys! PRODUCT DETAILS: Approx. 7\" long including the split ring to hold keys. Machine stitched over quality cotton fabric and firm interfacing on inside for structure yet comfortable to hold."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/41J3kMGt34L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41J3kMGt34L.jpg", "variant": "MAIN", "hi_res": null}, {"thumb": "https://m.media-amazon.com/images/I/41slBR2YGOL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41slBR2YGOL.jpg", "variant": "PT01", "hi_res": null}, {"thumb": "https://m.media-amazon.com/images/I/41++pwWvfcL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41++pwWvfcL.jpg", "variant": "PT02", "hi_res": "https://m.media-amazon.com/images/I/51TpGYCdKIL._SL1000_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41JKZoroL3L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41JKZoroL3L.jpg", "variant": "PT03", "hi_res": "https://m.media-amazon.com/images/I/515dUKmwsbL._SL1000_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41xHTeVPFOL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41xHTeVPFOL.jpg", "variant": "PT04", "hi_res": "https://m.media-amazon.com/images/I/513oIHPmgUL._SL1000_.jpg"}], "videos": [], "store": "Generic", "categories": ["Handmade Products", "Clothing, Shoes & Accessories", "Luggage & Travel Gear", "Key & Identification Accessories", "Keychains & Keyrings"], "details": {"Package Dimensions": "8 x 4 x 0.85 inches; 0.81 Ounces", "Department": "womens", "Date First Available": "September 11, 2018"}, "parent_asin": "B07NTK7T5P", "bought_together": null}
{"main_category": "Handmade", "title": "Anemone Jewelry Beauteous November Birthstone Ring - Citrine Ring in 14k Gold-filled Band Sizes 3-12.5 - Handcrafted Citrine Jewelry for Women for Any Occasion - Free Jewelry Box", "average_rating": 4.1, "rating_number": 10, "features": ["Stunning gemstone and detailed design", "Bands are customizable, with metals to choose from", "Engraving available", "Nickel Free and Tarnish Resistant", "Free gift box"], "description": ["Anemone brings this November birthstone ring that showcases elegance and style. This November birthstone jewelry features a beautiful faceted Citrine gemstone set in a 14k gold-filled double band, customizable to solid gold and 925 Sterling silver. You will surely love to wear this Citrine ring to formal events and even as everyday jewelry or a gift to November ladies. The perfect classy highlight piece."], "price": 69.0, "images": [{"thumb": "https://m.media-amazon.com/images/I/31C2XBkQFTS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/31C2XBkQFTS.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/71PTBqCt5GS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41r77nl2RIS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/41r77nl2RIS.jpg", "variant": "PT01", "hi_res": "https://m.media-amazon.com/images/I/91E7-S9hZmS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/319ny7M8GrS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/319ny7M8GrS.jpg", "variant": "PT02", "hi_res": "https://m.media-amazon.com/images/I/71yoTdkzALS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/31yNw8W8DdS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/31yNw8W8DdS.jpg", "variant": "PT03", "hi_res": "https://m.media-amazon.com/images/I/71wYvMH6CyS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/31OTPrnv5DS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/31OTPrnv5DS.jpg", "variant": "PT04", "hi_res": "https://m.media-amazon.com/images/I/71vasevirsS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/318pd1FZMpS._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/318pd1FZMpS.jpg", "variant": "PT05", "hi_res": "https://m.media-amazon.com/images/I/71qiHAavLaS._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/51pEwhx0BmL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/51pEwhx0BmL.jpg", "variant": "PT06", "hi_res": null}], "videos": [], "store": "Anemone Jewelry", "categories": ["Handmade Products", "Jewelry", "Rings", "Statement"], "details": {"Department": "womens", "Date First Available": "July 30, 2017"}, "parent_asin": "B0751M85FV", "bought_together": null}
{"main_category": "Handmade", "title": "Silver Triangle Earrings with Chevron Pattern", "average_rating": 5.0, "rating_number": 1, "features": [], "description": ["These large silver triangles are stamped with a unique chevron pattern, adding a statement to any outfit. Made of tarnish resistant argentium silver, each pair is one of a kind, and hang from handcrafted argentium silver earring wires. Triangle measures approximatly 1 1/2\" long."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/514fSLJnX9L._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/514fSLJnX9L.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/91wlFDKyz9L._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/51riti9SiXL._SS40_.jpg", "large": "https://m.media-amazon.com/images/I/51riti9SiXL.jpg", "variant": "PT01", "hi_res": "https://m.media-amazon.com/images/I/81YHcIyddKL._SL1500_.jpg"}], "videos": [], "store": "Zo\u00eb Noelle Designs", "categories": ["Handmade Products", "Jewelry", "Earrings", "Drop & Dangle"], "details": {"Department": "Women", "Date First Available": "July 4, 2016"}, "parent_asin": "B01HYNE114", "bought_together": null}
{"main_category": "Gift Cards", "title": "Amazon.com Gift Card in Gift Tag (Various Designs)", "average_rating": 4.8, "rating_number": 1006, "features": ["Gift Card is affixed inside a gift tag", "Gift amount may not be printed on Gift Cards", "Gift Card has no fees and no expiration date", "No returns and no refunds on Gift Cards", "Gift Card is redeemable towards millions of items storewide at Amazon.com", "Scan and redeem any Gift Card with a mobile or tablet device via the Amazon App", "Free One-Day Shipping (where available)", "Customized gift message, if chosen at check-out, only appears on packing slip and not on the actual gift card or carrier"], "description": ["Amazon.com Gift Cards are the perfect way to give them exactly what they're hoping for - even if you don't know what it is. Amazon.com Gift Cards are redeemable for millions of items across Amazon.com. Item delivered is a single physical Amazon.com Gift Card nested inside or with a free gift accessory."], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/41ZA96xtATL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41ZA96xtATL.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/71cWJvVGYtL._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41NK1FX6uUL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41NK1FX6uUL.jpg", "variant": "PT01", "hi_res": "https://m.media-amazon.com/images/I/71q-qp4b3-L._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/41Y45S0GirL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/41Y45S0GirL.jpg", "variant": "PT02", "hi_res": "https://m.media-amazon.com/images/I/71KutAnl9gL._SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/417MZ16DhcL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/417MZ16DhcL.jpg", "variant": "PT03", "hi_res": "https://m.media-amazon.com/images/I/61FMUKaXfJL._SL1175_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/21-tRQuOBZL._SX38_SY50_CR,0,0,38,50_.jpg", "large": "https://m.media-amazon.com/images/I/21-tRQuOBZL.jpg", "variant": "PT12", "hi_res": "https://m.media-amazon.com/images/I/61blLcj3pWL._SL1500_.jpg"}], "videos": [], "store": "Amazon", "categories": ["Gift Cards", "Gift Card Recipients", "For Him"], "details": {"Package Dimensions": "5 x 3 x 0.1 inches; 0.63 Ounces", "Item model number": "Fixed", "Date First Available": "August 29, 2017", "Manufacturer": "Amazon"}, "parent_asin": "B06ZXTKYHN", "bought_together": null}
{"main_category": "SUBSCRIPTION BOXES", "title": "Loved Again Media - Movie Subscription Box - 10 DVD Box - Pick Your Genres", "average_rating": 4.1, "rating_number": 75, "features": ["10 gently used DVDs delivered to your door every month.", "All titles are currated to your selection of specific genres. With 14+ genres for you to build your box from.", "We have hundreds of thousands of movies to pick from so this box will remain unique to your taste for years to come.", "Choose from Action, Adventure, Horror, Kids, Drama, Comedy, Romance, Thrillers, Documentaries, and a ton more!", "Help the environment by keeping used media out of landfills and by giving it a second life. We do our best to minimize all waste and rehome the movies."], "description": [], "price": null, "images": [{"thumb": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61FfqGgIMNL._AC_.jpg", "variant": "MAIN", "hi_res": "https://m.media-amazon.com/images/I/91Qthwjgl+L._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61tLIBYnjhL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61tLIBYnjhL._AC_.jpg", "variant": "PT11", "hi_res": "https://m.media-amazon.com/images/I/915mdTERF4L._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61+67xLBGlL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61+67xLBGlL._AC_.jpg", "variant": "PT12", "hi_res": "https://m.media-amazon.com/images/I/91KqI4Q3CtL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/6186F+gYXTL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/6186F+gYXTL._AC_.jpg", "variant": "PT13", "hi_res": "https://m.media-amazon.com/images/I/91iMalZnUIL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61sDRGqoT2L._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61sDRGqoT2L._AC_.jpg", "variant": "PT14", "hi_res": "https://m.media-amazon.com/images/I/91Q3UAI6CdL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61Rn7cebWaL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61Rn7cebWaL._AC_.jpg", "variant": "PT15", "hi_res": "https://m.media-amazon.com/images/I/91YykP+uBnL._AC_SL1500_.jpg"}, {"thumb": "https://m.media-amazon.com/images/I/61b3iiv+EVL._AC_US40_.jpg", "large": "https://m.media-amazon.com/images/I/61b3iiv+EVL._AC_.jpg", "variant": "PT16", "hi_res": "https://m.media-amazon.com/images/I/91dLynCDvWL._AC_SL1500_.jpg"}], "videos": [], "store": "Loved Again Media", "categories": [], "details": {}, "parent_asin": "B08W5BSH6V", "bought_together": null}
```

입력은 다음 조건을 만족해야 한다.

- 한 줄에 JSON object 1개
- UTF-8 인코딩
- blank line 허용 여부는 정책으로 정한다

입력은 반드시 streaming 방식으로 받아야 한다.

- multipart/form-data는 사용하지 않는다.
- request body stream을 그대로 읽는다.
- 파일이 매우 크므로 전체를 메모리에 적재하지 않는다.

### 3.2 출력

bulk upload는 다음 요약 정보를 반환한다.

- 총 line 수
- 파싱 성공 건수
- 배치 성공 건수
- 실패 건수
- 스킵 건수
- 실패 JSONL 상대경로

### 3.3 실패 데이터 저장

실패한 데이터는 재업로드를 위해 별도 JSONL 파일로 저장한다.

MySQL JDBC batch의 특성상, 배치 실패 시점에 정확히 어떤 line이 실패했는지 항상 식별할 수는 없다.  
따라서 실패 시에는 해당 batch 스냅샷 전체를 JSONL로 저장하는 정책을 기본으로 둔다.

저장 규칙:

- 실패한 batch 스냅샷 전체를 저장한다.
- 성공한 batch는 저장하지 않는다.
- 원본 line JSON 문자열을 그대로 저장한다.
- 추후 동일한 batch upload 로직으로 다시 업로드할 수 있어야 한다.
- 실패 저장 디렉터리는 `failedJsonl`로 고정한다.
- 파일명은 `?filename=` 쿼리 파라미터로 받는다.
- 저장 시 `.jsonl` 확장자를 자동으로 붙인다.
- append 시 구분자는 줄바꿈으로 한다.
- 동일한 `filename`으로 다시 요청되면 같은 파일에 계속 append한다.
- 실패가 없어도 빈 `.jsonl` 파일을 생성한다.

### 3.4 비범위

이 문서는 다음을 다루지 않는다.

- 파일 업로드 이력 저장
- 분산 처리
- 병렬 worker 처리
- 재시도 큐 오케스트레이션
- chunk 기반 분산 스케줄링

---

## 4. 핵심 설계 원칙

### 4.1 Streaming first

입력은 `InputStream` 또는 `Reader` 기반으로 처리한다.

금지 사항:

- 전체 파일을 `String`으로 읽는 방식
- 전체 JSONL을 `List<String>`으로 메모리에 올리는 방식

### 4.2 Line isolation

각 line은 독립적으로 처리한다.

즉, line 하나가 파싱 실패하더라도 다른 line 처리에는 영향을 주지 않는다.

### 4.3 Batch first

bulk upload는 단건 insert를 반복하는 방식이 아니라, JDBC batch 저장을 우선으로 설계한다.

이유:

- 단건 insert 반복은 시간이 너무 오래 걸린다.
- JDBC batch는 DB round-trip 수를 줄인다.
- 대용량 업로드에 더 적합하다.

### 4.4 Summary over hard fail

bulk upload의 기본 정책은 "가능한 만큼 끝까지 batch로 처리하고 결과를 요약"하는 것이다.

즉, 일부 line이 실패해도 전체 요청은 중단하지 않고, 실패한 line만 별도 JSONL로 저장한다.

---

## 5. 제안 패키지 구조

```text
jeong.awsshop.product.bulkupload
├── controller
│   └── BulkUploadController
├── service
│   ├── BulkUploadService
│   ├── BulkUploadLineParser
│   ├── BulkUploadBatchAssembler
│   ├── BulkUploadBatchWriter
│   └── BulkUploadFailureWriter
├── dto
│   ├── BulkUploadResponse
│   ├── BulkUploadRecord
│   ├── BulkUploadBatch
│   └── BulkUploadFailureRecord
└── repository
    └── ProductRepository
```

설계상 책임은 다음과 같다.

- `BulkUploadController`: HTTP 요청 수신, `batch-size`와 `filename` 파라미터 수신, 스트림 전달
- `BulkUploadService`: line-by-line 파싱, batch 조립, batch 업로드 오케스트레이션, 실패 결과 집계
- `BulkUploadLineParser`: 개별 line을 객체로 변환
- `BulkUploadBatchAssembler`: 객체들을 batch size 단위로 묶음
- `BulkUploadBatchWriter`: JDBC batch 저장
- `BulkUploadFailureWriter`: 실패 line을 JSONL로 저장
- `ProductRepository`: batch 저장 대상 테이블의 persistence 경계

실제 구현 시점에서는 upload 전용 repository가 아니라, batch insert 대상 도메인별 repository 또는 JDBC writer를 사용할 수 있다.  
이 문서에서의 `repository`는 bulk upload가 의존하는 persistence 경계를 의미한다.

---

## 6. API 설계

### 6.1 업로드 API

예시 엔드포인트:

```http
POST /api/products/bulk-upload/jsonl?batch-size=100&filename=failed-products
```

요청 형식은 `application/octet-stream` 또는 `text/plain` body stream이다.

이유:

- 파일이 매우 커서 multipart 업로드를 전제로 할 수 없다.
- controller가 request body stream을 그대로 읽어야 한다.

### 6.2 batch-size 쿼리 파라미터

controller는 `?batch-size=` 파라미터를 받는다.

예시:

```http
POST /api/products/bulk-upload/jsonl?batch-size=500
```

정책:

- `batch-size`는 양의 정수여야 한다.
- 값이 없으면 기본 batch size `100`을 사용한다.
- 최소값은 `1`이다.
- 최대값은 `1000`이다.
- 범위를 벗어나면 요청을 거절한다.

### 6.3 filename 쿼리 파라미터

controller는 `?filename=` 파라미터를 받는다.

정책:

- 실패 JSONL 파일명으로 사용한다.
- 비어 있으면 요청을 거절한다.
- 확장자는 입력받지 않는다.
- 저장 시 자동으로 `.jsonl`을 붙인다.
- 상대경로는 `failedJsonl/{filename}.jsonl` 형태로 반환한다.

### 6.4 응답 API

응답은 업로드 결과 요약 객체다.

권장 응답 필드:

- `totalLines`
- `parsedLines`
- `batchedLines`
- `successCount`
- `failureCount`
- `skippedCount`
- `failedJsonlLocation`

정의:

- `parsedLines`: JSON 파싱에 성공한 line 수
- `batchedLines`: 실제로 batch writer에 전달된 batch 개수
- `totalLines`: blank line을 포함한 전체 line 수
- `successCount`: 저장에 성공한 record 수
- `failureCount`: 저장에 실패한 record 수
- `skippedCount`: blank line으로 제외된 line 수

---

## 7. Controller 책임

`BulkUploadController`는 HTTP 계층만 담당한다.

담당 책임:

- 업로드 요청 수신
- `batch-size` 파라미터 수신
- `filename` 파라미터 수신
- 파일 또는 body stream 획득
- `BulkUploadService` 호출
- bulk upload 응답 반환

담당하지 않을 책임:

- JSON 파싱
- line 분기
- batch 조립
- DB 저장 세부 처리
- 실패 데이터 JSONL 저장 세부 처리
- 트랜잭션 관리

### 7.1 Controller 처리 원칙

- request body를 한 번에 문자열로 읽지 않는다.
- 파일이 비어 있으면 service 호출 전에 검증한다.
- content type 검증은 선택 사항이지만, 최소한 입력이 stream 가능한 형태인지 확인해야 한다.
- `batch-size`는 controller 단계에서 1차 검증한다.
- `filename`은 controller 단계에서 1차 검증한다.

---

## 8. Service 책임

`BulkUploadService`는 bulk upload의 orchestration 계층이다.

담당 책임:

- `Reader` 또는 `InputStream`을 line 단위로 순회
- blank line 정책 적용
- 각 line을 객체로 변환
- batch size 단위로 객체 묶기
- batch write 요청 수행
- 실패 데이터 수집
- 실패 line을 JSONL로 저장 요청
- 최종 요약 생성

담당하지 않을 책임:

- 개별 상품 필드 매핑
- entity 생성 상세 로직
- JDBC 세부 구현
- 파일 저장 세부 구현

### 8.1 line 처리 흐름

각 line은 다음 순서로 처리한다.

1. line 번호를 기록한다.
2. 공백 line인지 확인한다.
3. 유효한 JSON이면 객체로 변환한다.
4. 변환된 객체를 현재 batch에 추가한다.
5. batch size에 도달하면 batch write를 수행한다.
6. batch write 실패 시 해당 batch 데이터를 실패 JSONL로 보낸다.
7. 성공/실패 결과를 누적한다.

### 8.2 결과 집계

서비스는 처리 결과를 다음과 같이 분류한다.

- `SUCCESS`: 상품 저장 성공
- `SKIPPED`: blank line 등 정책상 제외
- `FAILED`: 파싱 실패, batch write 실패, 실패 JSONL 저장 실패

### 8.3 실패 정책

기본 정책은 continue-on-batch-failure다.

- 한 line 파싱 실패는 해당 line만 실패로 남긴다.
- batch write 실패 시 해당 batch를 실패로 분리한다.
- MySQL JDBC batch에서는 개별 row 실패 위치를 항상 알 수 없으므로, 실패한 batch 스냅샷 전체를 JSONL로 저장한다.
- 마지막에는 전체 요약을 반환한다.

---

## 9. Repository 책임

bulk upload는 별도 upload 이력 테이블 없이 시작하는 것을 기본안으로 둔다.

따라서 repository 책임은 JDBC batch 저장을 위한 persistence 경계로 정의한다.

### 9.1 주요 repository

- `ProductRepository`
- `ProductFeatureRepository`
- `ProductDescriptionRepository`
- `ProductCategoryRepository`
- `ProductImageRepository`
- `ProductVideoRepository`
- `ProductBoughtTogetherRepository`

### 9.2 repository 사용 방식

bulk upload service는 단건 insert를 반복 호출하지 않는다.

대신 하나의 batch 안에서 `Product`를 먼저 한 번에 저장하고, 그 다음 child 엔티티를 각각 한 번에 저장한다.  
batch writer는 `Product` 저장 후 child 저장을 같은 트랜잭션 안에서 순서대로 수행한다.

이 구조의 장점:

- round-trip 수를 줄일 수 있다.
- 대량 저장 성능을 높일 수 있다.
- 실패 batch만 별도로 재시도하기 쉽다.

### 9.3 저장 원칙

- 하나의 batch 안에 `Product`와 child 저장을 함께 묶는다.
- 저장 순서는 `Product` 먼저, child는 구현 재량으로 저장한다.
- 저장 실패 시 해당 batch에 포함된 원본 line JSON 문자열 전체를 실패 JSONL로 분리한다.
- `parentAsin`은 유니크 기준으로 취급한다.

---

## 10. line 처리 상세 설계

### 10.1 blank line

blank line은 기본적으로 스킵한다.

스킵 기준:

- `null`
- trim 후 빈 문자열

### 10.2 JSON 파싱 실패

잘못된 JSON line은 실패로 기록한다.

요구 사항:

- line 번호를 포함한다.
- 원인 메시지를 포함한다.
- 전체 업로드를 중단하지 않는다.

### 10.3 필수 필드 누락

필수 필드:

- `parent_asin`
- `title`

누락 또는 blank이면 해당 line은 실패로 처리한다.

### 10.4 중복 parentAsin

이미 저장된 `parentAsin`이면 해당 line은 실패로 처리한다.

이 정책은 batch write 결과에서도 동일하게 유지한다.  
중복으로 인해 batch 전체가 실패하면, 해당 batch의 원본 line만 실패 JSONL로 저장한다.

### 10.5 persistence 실패

DB 저장 중 예외가 발생하면 해당 batch를 실패로 처리한다.

예상 가능한 원인:

- UNIQUE 충돌
- FK 오류
- JPA flush 실패

MySQL JDBC batch에서는 `BatchUpdateException`이 발생해도 정확한 실패 row를 항상 역산할 수 없으므로,  
실패한 batch 스냅샷 전체를 저장하는 정책으로 단순화한다.

---

## 11. 트랜잭션 설계

### 11.1 batch 단위 트랜잭션

각 batch는 독립 트랜잭션으로 처리하는 것을 기본안으로 둔다.

이유:

- 실패 batch 롤백이 간단하다.
- 성공 batch의 저장 결과를 유지할 수 있다.
- bulk upload에서 partial success를 허용하기 쉽다.

권장 흐름:

1. `Product` 저장
2. child 엔티티 저장
3. 동일 batch 트랜잭션 안에서 commit 또는 rollback

주의:

- `Product` 저장과 child 저장 사이에 중간 실패가 발생하면 동일 batch 전체를 rollback해야 한다.
- child 저장을 위해서는 `Product` 식별자 매핑이 필요하다.

### 11.2 전체 트랜잭션 비권장

전체 파일을 하나의 트랜잭션으로 묶지 않는다.

이유:

- 대용량에서 트랜잭션이 너무 길어진다.
- 실패 하나 때문에 전체 롤백되면 bulk upload의 장점이 사라진다.

---

## 12. batch 저장 전략

bulk upload는 단건 insert를 반복하지 않고 batch 저장을 직접 수행하는 방향으로 설계한다.

권장 흐름:

```text
HTTP 요청
  -> BulkUploadController
  -> BulkUploadService
  -> line reader
  -> line parser
  -> batch assembler
  -> JDBC batch writer
  -> success or failure summary
```

이 방식의 장점:

- 단건 insert 반복보다 빠르다.
- DB round-trip을 줄일 수 있다.
- batch size를 통해 처리량을 제어할 수 있다.

### 12.1 batch size 정책

batch size는 controller query parameter로 받는다.

정책:

- 최소 1 이상이어야 한다.
- 운영 안정성을 위해 상한을 둘 수 있다.
- 기본값은 `100`이다.
- 상한은 `1000`이다.

### 12.2 failure JSONL 재저장

batch write에 실패한 데이터는 원본 JSONL 형태를 유지해 별도 파일로 저장한다.

저장 목적:

- 동일한 파이프라인으로 재업로드
- 실패 원인 수정 후 재처리
- 실패 데이터만 선별 추적

주의:

- 실패 시에는 batch 전체 스냅샷을 요청 1건당 1개 파일에 append한다.
- MySQL JDBC batch에서 일부 row만 실패하더라도, 해당 batch 전체를 실패로 보고 원본 스냅샷 전체를 append한다.
- 정확한 실패 line 분리는 향후 batch splitting 또는 재시도 전략으로 별도 설계한다.

---

## 13. 결과 모델

### 13.1 BulkUploadResponse

bulk upload 응답은 요약 중심이어야 한다.

권장 필드:

- `totalLines`
- `parsedLines`
- `batchedLines`
- `successCount`
- `failureCount`
- `skippedCount`
- `failedJsonlLocation`

### 13.2 BulkUploadBatch

batch는 line을 직접 보관하는 논리 단위다.

권장 필드:

- `batchNumber`
- `records`
- `recordCount`

### 13.3 BulkUploadRecord

line 파싱 결과를 보관하는 객체다.

권장 필드:

- `lineNumber`
- `rawLine`
- `parsedObject`
- `parentAsin`

### 13.4 실패 JSONL 저장 정책

실패 저장 파일은 재업로드 가능한 JSONL 형식이어야 한다.

정책:

- line 단위로 저장한다.
- 실패한 line만 저장한다.
- 원본 내용을 변형하지 않는다.
- 성공 line은 저장하지 않는다.
- 구분자는 줄바꿈이다.

---

## 14. 예외 정책

bulk upload는 예외를 두 층으로 나눈다.

### 14.1 line-level 예외

line 하나 처리 중 발생하는 예외는 해당 line 실패로 변환되어 누적한다.

### 14.2 request-level 예외

아래는 요청 전체 실패로 볼 수 있다.

- 요청 body를 읽지 못한 경우
- 파일 타입이 아예 잘못된 경우
- I/O 스트림이 끊긴 경우

이 경우에는 controller가 4xx 또는 5xx 응답으로 종료할 수 있다.

---

## 15. 성능 및 메모리 고려

### 15.1 메모리 사용 제한

입력은 line 단위로 처리하므로 전체 파일 크기에 비례한 메모리 증가를 피한다.

### 15.2 큰 파일 대응

대용량 파일에서는 다음이 중요하다.

- line 단위 buffering
- 결과 누적 객체 크기 제한
- 실패 상세를 너무 많이 쌓지 않는 정책

필요하면 실패 목록 개수 상한을 둔다.

### 15.3 저장 성능

현재 단계에서는 최적화보다 correctness를 우선한다.

나중에 필요하면 다음을 고려할 수 있다.

- 일정 line 수마다 flush/clear
- chunk 단위 commit
- 병렬 ingestion

하지만 이 문서의 1차 목표에는 포함하지 않는다.

---

## 16. 테스트 관점

설계 기준 테스트는 다음을 검증해야 한다.

1. JSONL 스트림을 line 단위로 읽는가
2. blank line을 스킵하는가
3. line이 객체로 변환되는가
4. 객체가 batch size 단위로 묶이는가
5. batch write가 수행되는가
6. batch 실패 시 실패 line만 JSONL로 저장되는가
7. `batch-size` 쿼리 파라미터가 반영되는가
8. 저장 성공/실패 카운트가 정확한가

---

## 17. 구현 순서

1. controller API 형태 확정
2. `batch-size` 쿼리 파라미터 계약 정의
3. stream 입력 계약 정의
4. line parser DTO 정의
5. batch assembler 정의
6. JDBC batch writer 정의
7. 실패 JSONL 저장 정책 정의
8. bulk upload summary DTO 정의
9. 테스트 기준 문서화

---

## 18. 결론

`BulkUploadService`는 JSONL 파일을 한 번에 문자열로 읽지 않고, streaming 방식으로 line-by-line 파싱한 뒤 batch size 단위로 묶어 JDBC batch로 저장하는 업로드 오케스트레이터다.

이 유스케이스의 핵심은 다음이다.

1. 메모리 효율성 확보
2. line 단위 파싱
3. batch size 기반 묶음 처리
4. JDBC batch 저장
5. 실패 line만 JSONL로 별도 저장
6. 업로드 결과 요약 반환

이 설계를 기준으로 controller, service, repository 경계를 먼저 고정한 뒤 구현하면, 이후 chunk 처리나 병렬화 같은 확장도 자연스럽게 붙일 수 있다.
