# Product Stream Upload Guide

`product-stream-upload.js`는 `meta-download-urls.txt`에 있는 Amazon 2023 meta dataset URL을 순서대로 다운로드하고, 압축을 풀면서 Spring 서버로 스트리밍 업로드한다. 이후 Spring은 DB로 업로드한다.

## 목적
aws-dataset의 각 파일은 용량이 굉장히 크다. 
따라서 다운로드한 파일을 DISK에 저장하기보다는, 바로 streaming으로 압축을 풀면서 업로드하는 방식을 사용한다.


## 실행 방법

### 1. 우선 Spring 서버를 먼저 실행해야한다.

```bash
./gradlew bootRun
```

### 2. 이후 node를 사용하여 js 파일을 실행한다.
실행 옵션은 아래와 같다.

**옵션 없이 전체 URL 실행**

```bash
node aws-dataset/product-stream-upload.js
```

옵션이 없으면 `meta-download-urls.txt`의 처음 URL부터 끝까지 순서대로 실행한다. 단, 진행 상태 파일에서 이미 `completed`로 기록된 URL은 건너뛴다.

**특정 라인 url 만 실행**

`meta-download-urls.txt`의 특정 라인만 실행할 수 있다. 라인 번호는 파일 기준 1부터 시작한다.

10번째 줄만 실행:

```bash
node aws-dataset/product-stream-upload.js --line 14
```

10번째 줄부터 15번째 줄까지 실행:

```bash
node aws-dataset/product-stream-upload.js --from-line 10 --to-line 15
```

10번째 줄부터 파일 끝까지 실행:

```bash
node aws-dataset/product-stream-upload.js --from-line 10
```

파일 처음부터 10번째 줄까지 실행:

```bash
node aws-dataset/product-stream-upload.js --to-line 10
```

라인 범위를 지정해도 이미 `completed`로 기록된 URL은 건너뛴다.

### 3. 진행 상태 파일 갱신

저장이 모두 완료된 업로드는 다음 파일에 저장된다.

```text
aws-dataset/product-stream-upload-progress.json
```

**이 파일에는 다음 정보가 저장된다.**

- `totalCount`: 전체 URL 개수
- `completedCount`: 완료된 URL 개수
- `failedCount`: 실패한 URL 개수
- `runningCount`: 실행 중으로 기록된 URL 개수
- `lastStartedUrl`: 마지막으로 시작한 URL
- `lastCompletedUrl`: 마지막으로 완료한 URL
- `totalDurationMs`: 완료된 작업의 총 소요시간
- `selectedLineRange`: 이번 실행에서 지정한 라인 범위
- `selectedCount`: 이번 실행 대상 URL 개수
- `items`: URL별 상세 상태

URL별 상세 상태에는 `status`, `filename`, `downloadUrl`, `uploadUrl`, `lineNumber`, `startedAt`, `completedAt`, `durationMs`, `error`가 기록된다.


### 4. 실패된 row 파일 생성
실패한 batch(100개 단위) row들이 JSONL 파일에 저장된다. 데이터 타입이 다르거나 DB 에러가 발생하여 실패한 batch에 대해, 해당 데이터들을 모은 파일을 생성한다. 
- URL에 따라, 실패한 row를 저장하는 파일이 생성된다. /aws-dataset/failed-products 에 category 명으로 실패한 batch rows들이 JSONL파일로 저장된다.




---

## 재시작 동작

스크립트가 중간에 종료되어도 다음 실행에서 `product-stream-upload-progress.json`을 읽는다.

기본 재시작 규칙:

- `completed` URL은 건너뛴다.
- 이전 실행에서 `running`으로 남은 URL도 건너뛴다.
- `failed` URL은 다시 시도한다.
- 아직 기록이 없는 URL은 새로 처리한다.

이전 실행에서 `running`으로 남은 URL을 다시 시도해야 한다면 다음 옵션을 사용한다.

```bash
node aws-dataset/product-stream-upload.js --retry-incomplete
```

## 입력 URL 파일

현재 스크립트는 다음 파일을 입력으로 사용한다.

```text
aws-dataset/meta-download-urls.txt
```

URL은 한 줄에 하나씩 작성한다. 빈 줄과 `#`로 시작하는 줄은 무시된다.

## 주의사항

이 스크립트는 다운로드 파일을 디스크에 저장하지 않는다. 원격 `.jsonl.gz` 응답을 받아 `gunzip`으로 압축을 풀고, 풀린 JSONL 스트림을 바로 업로드 요청 본문으로 전달한다.

서버가 업로드 중간에 종료되면 해당 URL은 `failed` 또는 `running` 상태로 남을 수 있다. 중복 업로드를 피하려면 기본 실행을 사용하고, 해당 URL을 다시 올려야 하는 상황에서만 `--retry-incomplete`를 사용한다.

## 참고

업로드 서버 주소를 바꾸려면 `UPLOAD_URL` 환경변수를 사용한다.

```bash
UPLOAD_URL='http://localhost:8080/api/data-import/upload' node aws-dataset/product-stream-upload.js
```
