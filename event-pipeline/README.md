# Event Pipeline

사용자 행동 이벤트 파이프라인을 기존 `src/main` 애플리케이션과 분리해서 실행하기 위한 별도 서버 모듈이다.

## Modules

- `common`: 이벤트 메시지와 이벤트 타입 공통 라이브러리. 직접 실행하지 않는다.
- `producer-api`: 사용자 행동 이벤트를 HTTP로 받고 Kafka에 발행하는 서버.
- `db-consumer`: Kafka 이벤트를 구독해서 DB에 저장하는 consumer 서버.
- `hadoop-consumer`: Kafka 이벤트를 구독해서 Hadoop 적재 전 단계의 JSONL 파일로 저장하는 consumer 서버.
- `kafka-connect`: Elasticsearch Sink Connector 등록 설정.
- `docker`: Kafka 3 broker, Elasticsearch, Kafka Connect 실행용 Compose 파일.

## Run

필요하면 `event-pipeline/.env` 값을 로컬 환경에 맞게 조정한다. 커밋용 기본값은 `event-pipeline/.env.example`에 둔다.

```bash
docker compose -f event-pipeline/docker/compose-event-pipeline-local.yml up -d
./gradlew :event-pipeline:producer-api:bootRun
./gradlew :event-pipeline:db-consumer:bootRun
./gradlew :event-pipeline:hadoop-consumer:bootRun
```

Producer API 기본 포트는 `18081`이다.

```bash
curl -X POST http://localhost:18081/api/event-pipeline/events/search \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"keyword":"macbook"}'
```

## Kafka Connect

Kafka Connect 서버가 뜬 뒤 Elasticsearch Sink Connector 플러그인이 설치되어 있어야 아래 설정을 등록할 수 있다.

```bash
curl -X POST http://localhost:18083/connectors \
  -H 'Content-Type: application/json' \
  --data @event-pipeline/kafka-connect/elasticsearch-sink.json
```

현재 Compose의 Kafka Connect 이미지는 worker 서버 실행용이다. Elasticsearch Sink Connector 플러그인은 운영 이미지에 포함하거나 Confluent Hub로 설치해야 한다.
