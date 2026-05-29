# 왜 Payment 생성에 Named Lock을 썼는가

## 문제
- `payment` 테이블에는 `(order_id, status)` unique key가 있다.
- 같은 `orderId`, 같은 `status=NOT_STARTED`로 50개 요청이 동시에 들어오면,
  기대는 `1건 성공 + 49건 409`였다.
- 하지만 실제로는 일부 요청이 `409 duplicate`가 아니라 `500 lock wait timeout`으로 실패했다.

## 실제로 깨진 지점
- 각 요청은 같은 `(order_id, NOT_STARTED)` 값을 insert하려고 했다.
- InnoDB는 이 unique key 슬롯에 대해 insert 충돌을 검사하는 동안 락 경합을 만든다.
- 첫 요청이 커밋되기 전까지 뒤 요청들은 duplicate 판정을 바로 받지 못하고 기다릴 수 있다.
- 이 대기가 길어지면 duplicate가 아니라 `Lock wait timeout exceeded`가 나온다.

즉 문제의 본질은:
- "중복 생성 방지"를 애플리케이션에서 제어하지 않고
- DB unique key 충돌에 그대로 맡겼다는 점이다.

## 왜 짧은 트랜잭션만으로는 부족했는가
- 처음에는 `createPayment()`의 write 구간을 짧게 만들면 해결될 수 있다고 봤다.
- 그래서 JPA `save()` 대신 더 짧은 `JdbcTemplate INSERT` 경로로 바꿨다.
- 이건 timeout 확률을 줄일 수는 있지만,
  여전히 여러 요청이 같은 unique key 위치에 동시에 진입하는 구조는 그대로였다.

결국:
- 트랜잭션을 짧게 만드는 건 "완화"다.
- 같은 키로 동시에 insert 경쟁에 들어가는 구조 자체를 막지는 못한다.

## 해결 방향
- 해결하려면 같은 `orderId`에 대한 결제 생성은 한 번에 하나만 들어가게 해야 한다.
- 즉 DB unique key 충돌이 일어나기 전에,
  애플리케이션이 먼저 "이 orderId는 지금 누가 처리 중인지"를 직렬화해야 한다.

## 왜 Named Lock인가

### 1. 직렬화 기준이 명확했다
- 우리가 막고 싶은 건 "같은 `orderId`에 대한 동시 생성"이다.
- MySQL named lock은 문자열 키 단위로 직렬화할 수 있다.
- 그래서 `payment:create:{orderId}` 형태로 락 키를 만들면,
  같은 `orderId` 요청끼리만 직렬화할 수 있다.

### 2. 별도 락 테이블이 필요 없다
- 다른 방법으로는 별도 lock row 테이블을 만들고 `SELECT ... FOR UPDATE`를 걸 수도 있다.
- 하지만 지금은 빠르게 원인을 차단하고 검증하는 것이 목적이었다.
- named lock은 스키마 변경 없이 바로 적용할 수 있다.

### 3. unique key 경쟁 전에 진입을 막을 수 있다
- 핵심은 `INSERT` 이전에 먼저 줄을 세우는 것이다.
- named lock을 먼저 잡으면,
  동시에 50개가 들어와도 실제 `INSERT`는 한 번에 하나씩만 실행된다.
- 그러면 첫 요청이 insert+commit을 끝낸 뒤,
  다음 요청은 이미 존재하는 row를 보고 duplicate로 빠르게 정리된다.

### 4. 문제 원인과 정확히 맞닿아 있다
- 이번 버그는 "같은 unique key 슬롯 동시 경쟁"이 원인이었다.
- named lock은 그 경쟁 자체를 없애는 방법이다.
- 즉 증상을 가리는 게 아니라, 원인 지점 앞에서 진입을 제어한다.

## 적용 방식
- [PaymentCreateWriter.java](/mnt/c/Users/정석찬/Desktop/project/aws-shop/src/main/java/jeong/awsshop/payment/application/PaymentCreateWriter.java)에서
  `GET_LOCK('payment:create:{orderId}', timeout)`를 먼저 호출한다.
- 락을 잡은 connection에서 바로 `INSERT`를 수행한다.
- 끝나면 `RELEASE_LOCK(...)`로 락을 해제한다.

흐름은 다음과 같다.

1. 요청 A가 `payment:create:123` 락을 획득
2. 요청 B~Z는 같은 락 키에서 대기
3. 요청 A가 `INSERT` 후 종료
4. 요청 B가 락 획득 후 같은 row insert 시도
5. 이미 row가 있으므로 duplicate로 종료

이 구조에서는:
- 50개가 한 unique key 슬롯에 동시에 들어가 충돌하지 않는다.
- DB는 동시 insert 경합 대신, 애플리케이션이 의도한 순서대로 한 건씩 처리한다.

## 왜 이 방식이 현재 문제에 맞는가
- 지금 요구사항은 "같은 order에 대해 중복 결제 생성이 되면 안 된다"는 것이다.
- 이 요구사항은 row-level unique key만으로는 충분하지 않았다.
- 실제로는 "동일 order 생성 요청은 동시에 실행되면 안 된다"는 동시성 정책이 필요했다.
- named lock은 그 정책을 코드로 직접 표현하는 방법이다.

## 한계
- named lock은 MySQL 의존적이다.
- 락 키 설계와 timeout 값을 잘못 잡으면 또 다른 대기 문제가 생길 수 있다.
- 따라서 장기적으로는
  - order 선점 상태 관리
  - 기존 payment 재사용
  - 만료/복구 정책
같은 상위 플로우 설계가 더 바람직할 수 있다.

## 결론
- 문제는 단순한 duplicate key가 아니라,
  같은 `orderId/status`에 대한 동시 insert 경쟁이었다.
- 짧은 트랜잭션만으로는 경쟁 자체를 제거하지 못했다.
- 그래서 `INSERT` 전에 같은 `orderId` 요청을 직렬화할 수 있는 MySQL named lock을 사용했다.
- named lock을 쓴 이유는 "동일 order 결제 생성은 한 번에 하나만 처리한다"는 정책을
  가장 직접적으로 구현할 수 있었기 때문이다.
