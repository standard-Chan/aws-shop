# k6 Test Setting

## 목적

`GET /api/products/{id}` 상품 상세 조회 부하테스트에 사용할 실제 Product ID 목록을 준비한다.

`export-product-detail-ids.sh`는 루트 `.env`의 MySQL 접속 정보를 읽고, 현재 DB에 저장된 `product.id` 전체를 CSV 파일로 추출한다.

## 파일

- `export-product-detail-ids.sh`: Product ID 추출 스크립트
- `product-detail-ids.csv`: 추출 결과 파일. 스크립트 실행 시 생성된다.

## 실행

프로젝트 루트에서 실행한다.

```bash
bash k6/test-setting/export-product-detail-ids.sh
```

기본 출력 경로는 다음과 같다.

```text
k6/test-setting/product-detail-ids.csv
```

다른 출력 경로를 사용하려면 `OUTPUT_FILE`을 지정한다.

```bash
OUTPUT_FILE=k6/test-setting/product-detail-ids-local.csv \
bash k6/test-setting/export-product-detail-ids.sh
```

## 동작

스크립트는 다음 쿼리를 실행한다.

```sql
SELECT id
FROM product
ORDER BY id ASC;
```

CSV에는 헤더 없이 Product ID만 한 줄에 하나씩 저장된다. JavaScript 정밀도 손상을 피하기 위해 k6에서는 ID를 숫자로 변환하지 말고 문자열로 읽어 사용한다.

## 주의사항

- `mysql` CLI가 설치되어 있어야 한다.
- 루트 `.env`에 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`가 있어야 한다.
- `localhost`는 MySQL socket 대신 TCP 접속을 사용하도록 `127.0.0.1`로 변환한다.
- 전체 Product ID를 추출하므로 데이터가 많을수록 파일 크기와 실행 시간이 증가한다.
- 생성된 `product-detail-ids.csv`는 부하테스트 입력 데이터이며 커밋 대상이 아니다.
