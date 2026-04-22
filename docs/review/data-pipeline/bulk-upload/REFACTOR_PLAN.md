# Review Bulk Upload REFACTOR 계획

## 체크리스트

- [x] Controller의 `batch-size` 검증을 Spring Validation으로 전환한다.
- [x] Controller의 `filename` blank 검증을 Spring Validation으로 전환한다.
- [x] Spring Validation으로 처리하기 어려운 검증은 이번 범위에서 추가하지 않는다.
- [x] Service의 실패 JSONL path 생성 로직을 하나의 `Path` 변수 중심으로 단순화한다.
- [x] Product bulk upload와 동일한 전체 동작은 유지한다.
- [x] 테스트 코드는 리팩토링 단계에서 먼저 수정하지 않는다.
- [x] 리팩토링 후 `./gradlew test --tests 'jeong.awsshop.review.*'`로 GREEN을 확인한다.
- [x] Controller endpoint와 테스트/설계 문서의 endpoint 불일치를 정리한다.
- [x] Repository SQL 상수화와 parameter binding private method 분리를 진행한다.

---

## 1. REFACTOR 단계 원칙

`docs/TDD/refactor.md` 기준으로 이번 단계는 동작 변경 없이 코드 구조만 정리한다.

- 구현 전에 리팩토링 대상을 문서로 정리한다.
- 테스트 코드는 먼저 수정하지 않는다.
- 한 번에 하나의 리팩토링만 적용한다.
- 기존 GREEN 상태를 유지해야 한다.
- 요청하지 않은 인터페이스, 추상화, 확장 구조는 추가하지 않는다.

이번 문서는 계획 문서이며, 구현은 포함하지 않는다.

---

## 2. 요청 리팩토링 항목

### 2.1 Controller parameter 검증을 Spring Validation으로 전환

현재 상태:

- `ReviewBulkUploadController`에서 `if`로 `batchSize < 1 || batchSize > MAX_BATCH_SIZE`를 직접 검사한다.
- `filename == null || filename.isBlank()`도 controller 내부 `if`로 직접 검사한다.
- `DEFAULT_BATCH_SIZE`를 사용한 `batchSize == 0 ? DEFAULT_BATCH_SIZE : batchSize` 분기가 남아 있으나, `@RequestParam(defaultValue = "100")`와 앞선 range 검증 때문에 실질적으로 불필요하다.

리팩토링 방향:

- controller class에 `@Validated`를 적용한다.
- `batch-size` parameter에 `@Min(1)`, `@Max(1000)`을 적용한다.
- `filename` parameter에 `@NotBlank`를 적용한다.
- `defaultValue = "100"`은 유지한다.
- 수동 `if` 검증과 `batchSize == 0 ? ...` 분기를 제거한다.

예상 형태:

```java
@Validated
@RestController
@RequiredArgsConstructor
public class ReviewBulkUploadController {

    @PostMapping("/api/reviews/bulk-upload")
    public ResponseEntity<ReviewBulkUploadResponse> upload(
            HttpServletRequest request,
            @RequestParam(name = "batch-size", required = false, defaultValue = "100")
            @Min(1) @Max(1000) int batchSize,
            @RequestParam(name = "filename") @NotBlank String filename
    ) throws IOException {
        ReviewBulkUploadResponse response = reviewBulkUploadService.upload(
                request.getInputStream(),
                batchSize,
                filename
        );
        return ResponseEntity.ok(response);
    }
}
```

주의 사항:

- Spring MVC에서 method parameter validation이 활성화되어야 한다.
- 현재 프로젝트에 `spring-boot-starter-validation` 의존성이 없다면 `jakarta.validation` annotation이 컴파일되지 않거나 동작하지 않을 수 있다.
- 이 경우 validation 의존성 추가가 필요하다. 의존성 추가가 부담스럽다면 최소한 `batchSize`와 `filename` 검증을 service로 이동하는 대안을 사용한다.

Spring Validation으로 구현할 수 있는 항목:

- `batch-size` 최소값: `@Min(1)`
- `batch-size` 최대값: `@Max(1000)`
- `filename` blank 금지: `@NotBlank`

Spring Validation으로만 처리하기 애매한 항목:

- 파일명에 `/`, `..`, 경로 구분자 등이 포함되는 path traversal 방지
- `.jsonl` 확장자 중복 입력 방지

위 항목은 현재 테스트와 기존 계약에 포함되어 있지 않다. 동작 변경 가능성이 있으므로 이번 리팩토링에서 바로 추가하지 않고, 필요하면 별도 요구사항으로 다룬다.

### 2.2 파일 path 변수 단순화

현재 상태:

```java
String failedRowsDirectory = "./aws-dataset/reviews";
String failedJsonlLocation = filename + ".jsonl";
String failedRowsFilePath = failedRowsDirectory + "/" + failedJsonlLocation;
```

문제:

- 같은 의미의 path 조각이 여러 변수로 흩어져 있다.
- 문자열 `"/"` 결합으로 경로를 만든다.
- 실제 파일 path와 응답 location을 만들기 위해 변수가 과하게 많다.

리팩토링 방향:

- 실제 파일 쓰기에 사용할 `Path failedRowsPath` 하나를 중심으로 처리한다.
- 응답에 필요한 파일명은 `failedRowsPath.getFileName().toString()`으로 얻는다.
- 디렉터리 생성은 `failedRowsPath.getParent()`를 사용한다.

예상 형태:

```java
Path failedRowsPath = Paths.get("./aws-dataset/reviews", filename + ".jsonl");

Files.createDirectories(failedRowsPath.getParent());

try (BufferedWriter writer = Files.newBufferedWriter(
        failedRowsPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
)) {
    ...
}

return new ReviewBulkUploadResponse(failedRowsPath.getFileName().toString());
```

효과:

- path 관련 변수가 3개에서 1개로 줄어든다.
- OS별 경로 구분자 처리를 `Paths.get`에 맡길 수 있다.
- 응답값과 실제 파일명이 같은 source에서 나온다.

---

## 3. 추가 리팩토링 후보

아래 항목은 현재 코드에서 냄새가 있는 부분이다. 다만 이번 요청 범위를 넘을 수 있으므로, 사용자가 선택한 항목만 별도 진행한다.

### 3.1 Service의 batch flush 중복 제거

현재 `ReviewBulkUploadService`에는 batch insert 후 실패 row 기록과 buffer clear가 두 번 반복된다.

반복 위치:

- `buffer.size() >= batchSize`인 경우
- stream 종료 후 `!buffer.isEmpty()`인 경우

후보:

```java
private void flush(List<ReviewDto> buffer, BufferedWriter writer) throws IOException {
    List<ReviewDto> failedBatch = reviewBulkInsertRepository.bulkInsert(buffer);
    writeFailedRows(writer, failedBatch);
    buffer.clear();
}
```

주의:

- Product bulkService와 구조를 최대한 동일하게 유지하려는 요구가 있었으므로, 이 리팩토링은 Product와의 유사성을 조금 낮춘다.
- 따라서 이번 단계에서 바로 적용하기보다 사용자 확인 후 진행한다.

### 3.2 `writeFailedRows`의 한 줄 `if` 정리

현재:

```java
if (failedRows == null || failedRows.isEmpty()) return;
```

후보:

```java
if (failedRows == null || failedRows.isEmpty()) {
    return;
}
```

효과:

- 코드 스타일 일관성이 좋아진다.
- 동작 변경이 없다.

### 3.3 Repository SQL 상수화

현재 `ReviewBulkInsertRepository.bulkInsert` 안에 `reviewSql`, `imageSql` text block이 있다.

후보:

- `private static final String REVIEW_INSERT_SQL`
- `private static final String REVIEW_IMAGE_INSERT_SQL`

효과:

- `bulkInsert` 메서드가 짧아진다.
- SQL 변경 위치가 명확해진다.

주의:

- 작은 초기 적재용 코드이므로 과한 구조 분리는 피한다.
- SQL을 별도 class로 빼는 것은 이번 리팩토링 범위에서 제외한다.

### 3.4 Repository parameter binding private method 분리

현재 `bulkInsert` 내부에서 review parameter 10개와 image parameter 6개를 직접 세팅한다.

후보:

- `addReviewBatch(PreparedStatement reviewPs, ReviewDto dto, long reviewId)`
- `addImageBatch(PreparedStatement imagePs, ReviewImageDto image, long reviewId)`

효과:

- `bulkInsert` 흐름이 transaction, loop, execute 중심으로 읽힌다.
- parameter index 실수를 찾기 쉬워진다.

주의:

- class를 추가하지 않고 같은 repository 내부 private method로만 분리한다.
- 테스트 수정 없이 기존 repository 테스트가 그대로 통과해야 한다.

### 3.5 실패 시 반환 list의 의미 명확화

현재 repository는 실패 시 입력받은 `reviews` list를 그대로 반환한다.

장점:

- service가 실패 batch를 그대로 JSONL로 저장할 수 있다.
- Product bulk repository 흐름과 유사하다.

주의할 점:

- service가 같은 buffer list를 넘기고 이후 `clear()`하기 때문에, repository가 실패 list로 같은 reference를 반환하는 현재 구조에서는 `writeFailedRows`가 clear 이전에 실행되어야 한다.
- 현재 순서는 `bulkInsert -> writeFailedRows -> buffer.clear()`라서 정상이다.
- 이 순서를 바꾸는 리팩토링은 동작을 깨뜨릴 수 있으므로 금지한다.

보류 후보:

- repository 호출 시 service에서 `new ArrayList<>(buffer)` snapshot을 넘기는 방식.

보류 이유:

- Product bulkService와 완전히 같은 구조에서 멀어진다.
- batch마다 추가 list allocation이 생긴다.

### 3.6 Controller endpoint 정합성 정리

현재 endpoint는 `/api/reviews/bulk-upload`이다.

확인 결과:

- `ReviewBulkUploadControllerTest`와 `BULK_UPLOAD_SERVICE_DESIGN.md`는 `/api/reviews/bulk-upload`을 기준으로 한다.
- controller endpoint도 같은 경로로 맞춘다.

리팩토링 방향:

- 기존 테스트와 설계 문서 계약을 기준으로 `/api/reviews/bulk-upload`에 맞춘다.

주의:

- endpoint 정합성은 테스트 계약을 우선한다.

---

## 4. 권장 적용 순서

1. Controller에 Spring Validation 적용
2. controller 수동 `if` 검증 제거
3. `batchSize == 0 ? DEFAULT_BATCH_SIZE : batchSize` 제거
4. Service path 변수를 `Path failedRowsPath` 하나로 축소
5. `writeFailedRows` 한 줄 `if` 스타일 정리
6. repository SQL 상수화 여부 확인
7. repository parameter binding private method 분리 여부 확인

각 단계 후 review 테스트를 실행한다.

```bash
./gradlew test --tests 'jeong.awsshop.review.*'
```

---

## 5. 이번 리팩토링에서 하지 않을 것

- parser, assembler, failure writer, batch writer class 분리
- 실패 JSONL 저장 정책 변경
- 성공 row count 응답 추가
- product 존재 여부 검증 추가
- 중복 review 검증 추가
- endpoint 변경
- service에서 line-level validation 추가
- repository를 JPA 기반으로 재작성

---

## 6. 리팩토링 결과 ADR

### 6.1 Controller parameter validation

결정:

- `ReviewBulkUploadController`에 `@Validated`를 추가한다.
- `batch-size`는 `@Min(1)`, `@Max(1000)`으로 검증한다.
- `filename`은 `@NotBlank`로 검증한다.
- 기존 수동 `if` 검증과 `batchSize == 0 ? ...` 분기를 제거한다.

이유:

- 검증 의도를 annotation으로 드러낼 수 있다.
- 이미 프로젝트에 `spring-boot-starter-validation`과 `ConstraintViolationException` handler가 있다.
- controller method parameter validation 실패는 기존 공통 handler를 통해 400으로 변환된다.

### 6.2 Endpoint 정합성

결정:

- endpoint는 `/api/reviews/bulk-upload`로 맞춘다.

이유:

- 기존 controller test와 설계 문서가 `/api/reviews/bulk-upload`을 기준으로 한다.
- 리팩토링 후 GREEN을 유지하려면 테스트 계약을 우선해야 한다.

### 6.3 실패 JSONL path 단순화

결정:

- service에서 실패 파일 경로를 `Path failedRowsPath` 하나로 만든다.
- 디렉터리 생성은 `failedRowsPath.getParent()`를 사용한다.
- 응답 파일명은 `failedRowsPath.getFileName().toString()`을 사용한다.

이유:

- 문자열 path 조각 변수 3개를 하나의 `Path` 중심 처리로 줄인다.
- 실제 파일명과 응답 파일명이 같은 source에서 나온다.

### 6.4 Repository 내부 정리

결정:

- insert SQL을 `private static final` 상수로 올린다.
- review row parameter binding은 `addReviewBatch`로 분리한다.
- review image row parameter binding은 `addReviewImageBatch`로 분리한다.

이유:

- `bulkInsert`는 transaction 흐름과 batch 실행 흐름 중심으로 읽힌다.
- SQL과 parameter index 매핑이 각각 명확해진다.
- 별도 class나 interface를 추가하지 않아 초기 적재용 구조를 유지한다.

### 6.5 검증 결과

실행 명령:

```bash
./gradlew test --tests 'jeong.awsshop.review.*'
```

결과:

- BUILD SUCCESSFUL
