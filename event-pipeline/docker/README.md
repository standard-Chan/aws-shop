# Event Pipeline Docker

사용자 행동 이벤트 파이프라인의 Kafka, Elasticsearch, Kafka Connect 실행 설정이다.

## Local 1 Broker

로컬 개발용 단일 Kafka broker 구성이다. 가볍게 실행할 수 있지만 broker 장애 허용은 없다.

```bash
docker compose -f event-pipeline/docker/compose-event-pipeline-local.yml up -d
```

종료:

```bash
docker compose -f event-pipeline/docker/compose-event-pipeline-local.yml down
```

## Cluster 3 Brokers

부하 테스트와 운영 구조 검증용 3 broker 구성이다. replication factor 3, min ISR 2 기준으로 broker 1대 장애까지 견디는 구성을 확인할 수 있다.

```bash
docker compose -f event-pipeline/docker/compose-event-pipeline.yml up -d
```

종료:

```bash
docker compose -f event-pipeline/docker/compose-event-pipeline.yml down
```

## Ports

- Kafka local bootstrap: `localhost:19092`
- Kafka 3 broker bootstrap: `localhost:19092,localhost:19093,localhost:19094`
- Elasticsearch: `http://localhost:19200`
- Kafka Connect REST API: `http://localhost:18083`
- Product Ranking Redis: `localhost:16379`
- Product Ranking ClickHouse HTTP: `http://localhost:18123`
- Product Ranking ClickHouse native: `localhost:19000`

## Kafka Connect Connector

Kafka Connect 서버가 뜬 뒤 Elasticsearch Sink Connector 플러그인이 설치되어 있어야 connector를 등록할 수 있다.

```bash
curl -X POST http://localhost:18083/connectors \
  -H 'Content-Type: application/json' \
  --data @event-pipeline/kafka-connect/elasticsearch-sink.json
```
