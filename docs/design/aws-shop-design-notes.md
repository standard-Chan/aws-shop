# AWS Shop 설계 메모

## 1. 목표

AWS 공개 데이터셋인 Amazon Reviews 2023과 상품 메타데이터를 활용해 커머스 시스템을 구현한다.

이 프로젝트의 핵심 목적은 상품/리뷰/상세 조회 API를 만들며, 다음 역량을 기르는 것이다.

- 대용량 데이터 적재와 정제
- MySQL 기반의 정규화와 비정규화 설계
- Elasticsearch 기반 검색 인덱싱과 검색 품질 개선
- 읽기 트래픽 중심 서비스의 확장 전략 설계
- Java 21, Spring 기반의 서버 처리량 최적화
- DDD 관점의 도메인 분리와 서비스 경계 설정

기타 주문, 배송, 결제 시스템은 위 내용이 완료된 이후 구축한다.

## 2. 현재 가정한 서비스 상황

### 2.1 트래픽 가정

- 평균 동접: 100
- 평균 RPS: 100 RPS
- 피크 동접: 1000
- 피크 RPS: 800 RPS

### 2.2 요청 특성

GET 요청이 대부분을 차지한다.

- 상품 상세 조회: 30%
- 리뷰 조회: 30%
- 검색: 40%

### 2.3 데이터 규모

#### Amazon Reviews 2023

- 총 리뷰 수: 약 4,800만 건
- 압축 해제 용량: 약 35GB ~ 50GB
- 특징
  - 텍스트 길이 편차가 큼
  - `user_id`, `product_id`, `rating`, `review_text` 등 포함

#### Meta 데이터

- 총 상품 수: 약 2,000만 개
- 압축 해제 용량: 약 10GB ~ 20GB
- 포함 정보
  - `title`
  - `description`
  - `category`
  - `price`
  - `brand`
  - `image`

#### 전체 규모

- Raw 기준 70GB 이상
- DB 적재 및 인덱스 포함 시 80GB ~ 100GB 정도 가능

## 3. 초기 설계 방향

### 3.1 인프라 방향

- 수평 확장이 가능한 구조를 우선 고려한다.
- 도메인 단위로 서비스를 나누는 것을 전제로 한다.
- 장기적으로는 MSA 구조로 확장 가능해야 한다.

### 3.2 서버 스펙 가정

#### AWS 기준

- Application 서버: `t3.medium`
  - 2 vCPU
  - 4 GiB RAM
  - 월 약 $37.96

- DB 서버: `t3.large`
  - 2 vCPU
  - 8 GiB RAM
  - 월 약 $60 ~ $75

#### Google Cloud 무료 크레딧 기준

- Application 서버: `e2-medium`
  - 2 vCPU
  - 4 GB RAM
  - (JAVA Spring 이므로, 최소 4GB 메모리 필요)

- DB 서버: `e2-standard-2`
  - 2 vCPU
  - 8 GB RAM
  - (인덱스를 메모리에 올리기 위해 충분한 RAM 필요)

- 디스크: `pd-ssd` 또는 balanced SSD
  - 최소 100GB 이상
