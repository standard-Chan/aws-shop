# Review Bulk Upload RED 테스트 문서

**작성일**: 2026-04-22  
**대상 설계 문서**: `docs/review/data-pipeline/bulk-upload/BULK_UPLOAD_SERVICE_DESIGN.md`  
**범위**: Java / JUnit5 기반 RED 단계에서 작성할 필수 테스트 케이스 정의  
**주의**: 이 문서는 테스트 코드 작성 전 검토용 문서다. 구현 코드와 테스트 코드는 작성하지 않는다.

---

## 1. RED 단계 원칙

`docs/TDD/red.md` 기준으로 RED 단계는 다음 원칙을 따른다.

- 구현 코드는 작성하지 않는다.
- 테스트 코드는 컴파일되거나, 존재하지 않는 클래스/메서드 참조로 컴파일 실패해도 된다.
- 존재하지 않는 클래스를 우회하기 위한 리플렉션, 래퍼, 팩토리 메서드는 만들지 않는다.
- 테스트 본문에서 대상 클래스와 메서드를 직접 참조한다.
- 테스트는 class 단위로 분리한다.
- 테스트 메서드명은 `should_[기대결과]_when_[조건]` 형식을 사용한다.
- `@DisplayName`에는 한국어로 테스트 의도를 명시한다.
- Given / When / Then 주석을 사용한다.
- AssertJ `assertThat`을 사용한다.

---

## 2. 테스트 범위 축소 기준

review bulk upload는 초기 데이터 적재용이다.  
따라서 모든 세부 edge case를 테스트로 고정하지 않고, 작동에 필수적인 계약만 RED 테스트로 작성한다.

필수 계약은 다음이다.

1. HTTP 요청이 service로 올바르게 전달된다.
2. 필수 query parameter가 검증된다.
3. JSONL stream을 Jackson parser로 순차 처리한다.
4. 실제 review JSON object를 저장 가능한 DTO로 변환한다.
6. 정상 record를 batch size 단위로 repository에 전달한다.
7. repository가 반환한 실패 batch DTO를 실패 JSONL에 저장한다.
8. `review`, `review_image`, `attachment_type`을 DB insert 대상으로 전달한다.
9. repository insert 실패 시 실패 batch list를 반환한다.
10. batch size보다 적은 잔여 데이터도 insert한다.

테스트 대상 class는 다음 3개로 제한한다.

| 테스트 클래스 | 대상 클래스 | 책임 |
| --- | --- | --- |
| `ReviewBulkUploadControllerTest` | `ReviewBulkUploadController` | API parameter 검증, request stream 전달 |
| `ReviewBulkUploadServiceTest` | `ReviewBulkUploadService` | streaming parsing, batch 조립, 실패 JSONL 저장 |
| `ReviewBulkInsertRepositoryTest` | `ReviewBulkInsertRepository` | `review`, `review_image` JDBC batch insert |

---

## 3. 필수 테스트 케이스

### 3.1 Controller

| ID | 테스트 클래스 | 테스트 메서드명 | 목적                                                   | Given | When | Then |
| --- | --- | --- |------------------------------------------------------| --- | --- | --- |
| CT-001 | `ReviewBulkUploadControllerTest` | `should_call_service_with_request_body_stream_when_valid_request_is_given` | 데이터가 Stream으로 들어오는지를 검증한다.                           | valid body stream, `batch-size`, `filename` | bulk upload API 호출 | request body stream, batch size, filename을 service로 전달한다 |
| CT-002 | `ReviewBulkUploadControllerTest` | `should_reject_request_when_batch_size_is_out_of_range` | batch size가 잘못되면 대용량 적재가 위험하므로 controller 단계에서 차단한다. | `batch-size=0` 또는 `batch-size=1001` | bulk upload API 호출 | 4xx 응답을 반환하고 service를 호출하지 않는다 |
| CT-003 | `ReviewBulkUploadControllerTest` | `should_reject_request_when_filename_is_blank` | 실패 JSONL 저장 위치가 없으면 재처리가 불가능하므로 요청을 거절한다.            | blank filename | bulk upload API 호출 | 4xx 응답을 반환하고 service를 호출하지 않는다 |

### 3.2 Service

| ID | 테스트 클래스 | 테스트 메서드명 | 목적 | Given | When | Then |
| --- | --- | --- | --- | --- | --- | --- |
| SV-001 | `ReviewBulkUploadServiceTest` | `should_parse_and_upload_valid_review_jsonl_when_valid_stream_is_given` | 실제 JSONL object가 review/image 저장 DTO로 변환되는 가장 중요한 정상 흐름을 검증한다. | 실제 샘플 review JSONL 1건 | service upload 실행 | repository에 review 1건이 전달되고 실패 JSONL 파일명이 응답된다 |
| SV-002 | `ReviewBulkUploadServiceTest` | `should_map_image_fields_including_attachment_type_when_review_has_images` | image URL과 `attachment_type`이 저장 대상에 포함되는 계약을 고정한다. | image 1개와 `attachment_type` 포함 JSONL | service upload 실행 | repository에 image URL 3개와 attachment type이 전달된다 |
| SV-003 | `ReviewBulkUploadServiceTest` | `should_ignore_blank_lines_as_json_whitespace_when_stream_contains_blank_lines` | blank line이 Jackson whitespace로 처리되는 기본 JSONL 처리 정책을 검증한다. | blank line과 정상 line이 섞인 stream | service upload 실행 | 정상 line만 저장된다 |
| SV-004 | `ReviewBulkUploadServiceTest` | `should_flush_batch_when_record_count_reaches_batch_size` | batch size 단위로 repository를 호출하는 성능 핵심 계약을 검증한다. | 정상 line 3건, batch size 2 | service upload 실행 | repository가 2건 batch와 1건 batch로 호출된다 |
| SV-005 | `ReviewBulkUploadServiceTest` | `should_write_failed_batch_snapshot_when_repository_insert_fails` | JDBC batch 실패 시 개별 row가 아니라 batch DTO를 재처리 파일로 남기는 정책을 검증한다. | 정상 line 2건, repository batch insert 실패 | service upload 실행 | 실패한 batch DTO 2건이 실패 JSONL에 저장된다 |

### 3.3 Repository

| ID | 테스트 클래스 | 테스트 메서드명 | 목적 | Given | When | Then                                                   |
| --- | --- | --- | --- | --- | --- |--------------------------------------------------------|
| RP-001 | `ReviewBulkInsertRepositoryTest` | `should_insert_review_rows_when_valid_batch_is_given` | review parent row가 batch insert되는 persistence 핵심 계약을 검증한다. | review record batch | repository bulk insert 실행 | `review` insert batch가 실행된다                            |
| RP-002 | `ReviewBulkInsertRepositoryTest` | `should_insert_review_image_rows_with_attachment_type_when_records_have_images` | image row와 `attachment_type` 저장 계약을 검증한다. | attachment type 포함 image record batch | repository bulk insert 실행 | `review_image` insert에 URL 3개와 `attachment_type`이 포함된다 |
| RP-003 | `ReviewBulkInsertRepositoryTest` | `should_use_parent_asin_as_review_product_id_without_product_lookup` | product가 없는 review도 저장해야 하므로 product 조회가 없다는 계약을 고정한다. | product가 없는 `parent_asin` | repository bulk insert 실행 | product 조회 없이 `review.product_id`에 문자열을 세팅한다           |
| RP-004 | `ReviewBulkInsertRepositoryTest` | `should_rollback_entire_batch_when_review_image_insert_fails` | review와 image 저장이 같은 batch transaction으로 묶이는지 검증한다. | review insert 후 image insert 실패 | repository bulk insert 실행 | 해당 batch transaction이 rollback되고 예외를 발생시킨다             |
| RP-005 | `ReviewBulkInsertRepositoryTest` | `should_return_failed_batch_list_when_db_insert_fails` | DB insert 실패 batch를 service가 실패 JSONL로 저장할 수 있도록 반환 계약을 고정한다. | DB 에러가 발생하는 review batch | repository bulk insert 실행 | 실패한 batch list를 반환한다                                   |
| RP-006 | `ReviewBulkInsertRepositoryTest` | `should_insert_remaining_records_when_record_count_is_less_than_batch_size` | batch size보다 적은 마지막 잔여 데이터가 누락되지 않고 저장되는지 검증한다. | batch size보다 적은 review record list | repository bulk insert 실행 | 잔여 record도 `review`와 `review_image`에 insert된다          |

---

## 4. 제외한 테스트

아래 항목은 현재 단계에서 별도 RED 테스트로 작성하지 않는다.

- 모든 필수 field 각각의 누락 케이스
- `title` 511자 초과 전용 케이스
- `helpful_vote` 음수 전용 케이스
- `timestamp` 0 이하 전용 케이스
- unknown field 무시 전용 케이스
- 중복 review 미검증 전용 케이스
- 실패 없는 경우 빈 실패 JSONL 파일 생성 전용 케이스
- 대용량 stream 메모리 사용량 감시 테스트

이 항목들은 필요하면 green 이후 보강 테스트로 추가한다.

---

## 5. 확정 정책

- 테스트 대상 class는 `Controller`, `Service`, `Repository` 3개로 제한한다.
- parser, assembler, failure writer, batch writer는 별도 class로 분리하지 않는다.
- `attachment_type`은 `review_image.attachment_type`에 저장한다.
- 중복 review는 없다고 가정한다.
- 중복 review 검증은 하지 않는다.
- product 존재 여부는 검증하지 않는다.
- product가 없는 review도 저장한다.
- `parent_asin`은 `review.product_id`에 문자열로 저장한다.
- repository는 DB insert 실패 시 실패 batch list를 반환한다.
- batch size보다 적은 잔여 데이터도 insert 대상이다.

요청자가 이 문서를 검토한 뒤 테스트 케이스를 선택하거나 추가/삭제하면, 다음 단계에서 선택된 항목만 JUnit5 RED 테스트 코드로 작성한다.
