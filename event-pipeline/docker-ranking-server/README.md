# Product Ranking Docker Hub 배포

`event-pipeline:product-ranking`만 Docker image로 빌드해서 Docker Hub에 올리고, 원격 컴퓨터에서 pull/run 하기 위한 구성이다. Redis와 ClickHouse는 연결하지 않고 기본적으로 in-memory ranking store로 실행한다.

## 포함 파일

- `Dockerfile`: `common`과 `product-ranking` 모듈만 복사해 `classes`까지 미리 빌드하고 `bootRun`으로 실행한다.
- `Dockerfile.dockerignore`: Docker build context에서 필요한 파일만 전송한다.
- `.env.example`: 단독 실행용 환경변수 예시다.

## 로컬 빌드

push 전 로컬에서 이미지를 생성한다.

```bash
docker build \
  -f event-pipeline/docker-ranking-server/Dockerfile \
  -t <dockerhub-username>/aws-shop-product-ranking:1.2.1 \
  .
```

예시:

```bash
docker build \
  -f event-pipeline/docker-ranking-server/Dockerfile \
  -t jeongseokchan/aws-shop-product-ranking:1.2.1 \
  .
```

## 로컬 실행 확인
업로드 전 테스트를 진행한다. 

```bash
docker run --rm \
  --env-file event-pipeline/docker-ranking-server/.env.example \
  -p 8080:8080 \
  jeongseokchan/aws-shop-product-ranking:1.2.1
```

---

## Docker Hub 업로드

Docker Hub에 로그인한다.

```bash
docker login
```

이미지를 빌드한 이름 그대로 push한다.

```bash
docker push jeongseokchan/aws-shop-product-ranking:1.2.1
```

`latest` 태그도 같이 올리고 싶으면 tag 후 push한다.

```bash
docker tag \
  <dockerhub-username>/aws-shop-product-ranking:1.2.1 \
  <dockerhub-username>/aws-shop-product-ranking:latest

docker push <dockerhub-username>/aws-shop-product-ranking:latest
```

## 원격 컴퓨터에서 실행

원격 컴퓨터에 Docker가 설치되어 있어야 한다.

```bash
docker pull <dockerhub-username>/aws-shop-product-ranking:1.2.1
docker pull jeongseokchan/aws-shop-product-ranking:1.2.1
```

원격 컴퓨터에 `.env` 파일을 만든다.

```bash
EVENT_PRODUCT_RANKING_PORT=18083
EVENT_PRODUCT_RANKING_REDIS_ENABLED=false
EVENT_PRODUCT_RANKING_CLICKHOUSE_ENABLED=false
EVENT_PRODUCT_RANKING_BATCH_SIZE=10000
EVENT_PRODUCT_RANKING_BATCH_FLUSH_INTERVAL_MILLIS=3000
EVENT_PRODUCT_RANKING_BATCH_QUEUE_CAPACITY=500000
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
```

실행한다.

```bash
docker stop aws-shop-product-ranking
docker rm aws-shop-product-ranking
docker run -d \
  --name aws-shop-product-ranking \
  --env-file .env \
  -p 8080:8080 \
  --restart unless-stopped \
  jeongseokchan/aws-shop-product-ranking:1.2.1
  
```

로그 확인:

```bash
docker logs -f aws-shop-product-ranking

curl -I http://localhost:8080/
```

중지와 삭제:

```bash
docker stop aws-shop-product-ranking
docker rm aws-shop-product-ranking
```

## 주의 사항

- 현재 Gradle wrapper 9.4.1과 Spring Boot Gradle plugin 3.2.5 조합에서는 `bootJar` task가 실패할 수 있어, 이 Dockerfile은 저장소의 기존 load-test image와 동일하게 `bootRun` 실행 방식을 사용한다.
- Redis가 꺼져 있으므로 데이터는 컨테이너 메모리에만 저장된다. 컨테이너를 재시작하면 랭킹 데이터는 사라진다.
- ClickHouse도 꺼져 있으므로 장기 보관이나 장기 window 분석 저장소로 사용하지 않는다.
- 외부 공개 서버에서 실행할 때는 방화벽에서 `18083` 포트를 열어야 한다.
