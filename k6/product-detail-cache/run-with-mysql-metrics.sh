#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR="$ROOT_DIR/k6/product-detail-cache"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
SCENARIO="${SCENARIO:-${1:-uniform}}"
RESULT_DIR="${RESULT_DIR:-$TEST_DIR/results}"
SAMPLE_INTERVAL_SECONDS="${MYSQL_SAMPLE_INTERVAL_SECONDS:-1}"
TPS_VALUE="${TPS:-100}"
DURATION_VALUE="${DURATION:-1m}"
LOAD_LABEL="$(printf 'tps%s-duration%s' "$TPS_VALUE" "$DURATION_VALUE" | tr -c 'A-Za-z0-9._-' '-')"

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql CLI가 필요합니다. mysql client를 설치한 뒤 다시 실행하세요." >&2
  exit 1
fi

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 CLI가 필요합니다. k6를 설치한 뒤 다시 실행하세요." >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "node CLI가 필요합니다. 결과 JSON 병합에 사용됩니다." >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo ".env 파일을 찾을 수 없습니다: $ENV_FILE" >&2
  exit 1
fi

case "$SCENARIO" in
  uniform)
    K6_SCRIPT="$TEST_DIR/uniform-product-detail-cache.js"
    ;;
  zipf)
    K6_SCRIPT="$TEST_DIR/zipf-product-detail-cache.js"
    ;;
  event)
    K6_SCRIPT="$TEST_DIR/event-product-detail-cache.js"
    ;;
  *)
    echo "지원하지 않는 SCENARIO입니다: $SCENARIO (uniform, zipf, event 중 하나)" >&2
    exit 1
    ;;
esac

env_value() {
  local key="$1"
  local value
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- | tr -d '\r' || true)"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf '%s' "$value"
}

DB_HOST_VALUE="${DB_HOST:-$(env_value DB_HOST)}"
DB_PORT_VALUE="${DB_PORT:-$(env_value DB_PORT)}"
DB_NAME_VALUE="${DB_NAME:-$(env_value DB_NAME)}"
DB_USERNAME_VALUE="${DB_USERNAME:-$(env_value DB_USERNAME)}"
DB_PASSWORD_VALUE="${DB_PASSWORD:-$(env_value DB_PASSWORD)}"

DB_HOST_VALUE="${DB_HOST_VALUE#jdbc:mysql://}"
DB_HOST_VALUE="${DB_HOST_VALUE#mysql://}"
DB_HOST_VALUE="${DB_HOST_VALUE%%/*}"

if [ "$DB_HOST_VALUE" = "localhost" ]; then
  DB_HOST_VALUE="127.0.0.1"
fi

DB_PORT_VALUE="${DB_PORT_VALUE:-3306}"

if [ -z "$DB_HOST_VALUE" ] || [ -z "$DB_NAME_VALUE" ] || [ -z "$DB_USERNAME_VALUE" ]; then
  echo "DB_HOST, DB_NAME, DB_USERNAME 값이 필요합니다. $ENV_FILE 값을 확인하세요." >&2
  exit 1
fi

mkdir -p "$RESULT_DIR"
TMP_DIR="$(mktemp -d)"
BEFORE_FILE="$TMP_DIR/mysql-before.ndjson"
AFTER_FILE="$TMP_DIR/mysql-after.ndjson"
SAMPLES_FILE="$TMP_DIR/mysql-samples.ndjson"
K6_SUMMARY_FILE="$TMP_DIR/k6-summary.json"
RESULT_FILE="${RESULT_FILE:-$RESULT_DIR/${SCENARIO}-${LOAD_LABEL}.json}"
SAMPLER_PID=""

cleanup() {
  if [ -n "$SAMPLER_PID" ] && kill -0 "$SAMPLER_PID" >/dev/null 2>&1; then
    kill "$SAMPLER_PID" >/dev/null 2>&1 || true
    wait "$SAMPLER_PID" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mysql_status() {
  MYSQL_PWD="$DB_PASSWORD_VALUE" mysql \
    --protocol=TCP \
    --host="$DB_HOST_VALUE" \
    --port="$DB_PORT_VALUE" \
    --user="$DB_USERNAME_VALUE" \
    --database="$DB_NAME_VALUE" \
    --batch \
    --raw \
    --skip-column-names \
    --execute="SHOW GLOBAL STATUS WHERE Variable_name IN ('Queries','Questions','Com_select','Threads_connected','Threads_running','Connections','Created_tmp_disk_tables','Innodb_rows_read','Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads','Innodb_data_reads','Innodb_data_writes');"
}

status_value() {
  local status_file="$1"
  local key="$2"
  local default_value="${3:-0}"
  awk -v key="$key" -v default_value="$default_value" '$1 == key { print $2; found = 1 } END { if (!found) print default_value }' "$status_file"
}

cpu_sample() {
  if ! command -v powershell.exe >/dev/null 2>&1; then
    printf 'false\t0\t0\tpowershell.exe not found\n'
    return
  fi

  local output
  output="$(powershell.exe -NoProfile -Command '$process = Get-Process mysqld -ErrorAction SilentlyContinue | Select-Object -First 1; if ($null -eq $process) { Write-Output "false|0|0|mysqld process not found" } else { Write-Output ("true|" + $process.CPU + "|" + [Environment]::ProcessorCount + "|") }' 2>/dev/null | tr -d '\r' | tail -n 1 || true)"
  if [ -z "$output" ]; then
    printf 'false\t0\t0\tpowershell.exe returned no output\n'
    return
  fi

  IFS='|' read -r available cpu_seconds logical_processors reason <<< "$output"
  printf '%s\t%s\t%s\t%s\n' "${available:-false}" "${cpu_seconds:-0}" "${logical_processors:-0}" "${reason:-}"
}

collect_snapshot() {
  local output_file="$1"
  local status_file="$TMP_DIR/status-$(date +%s%N).tsv"
  mysql_status > "$status_file"

  local timestamp_epoch_ms
  timestamp_epoch_ms="$(node -e 'console.log(Date.now())')"

  local cpu_available cpu_seconds logical_processors cpu_reason
  IFS=$'\t' read -r cpu_available cpu_seconds logical_processors cpu_reason < <(cpu_sample)

  node -e '
    const snapshot = {
      timestampEpochMs: Number(process.argv[1]),
      mysql: {
        queries: Number(process.argv[2]),
        questions: Number(process.argv[3]),
        comSelect: Number(process.argv[4]),
        threadsConnected: Number(process.argv[5]),
        threadsRunning: Number(process.argv[6]),
        connections: Number(process.argv[7]),
        createdTmpDiskTables: Number(process.argv[8]),
        innodbRowsRead: Number(process.argv[9]),
        innodbBufferPoolReadRequests: Number(process.argv[10]),
        innodbBufferPoolReads: Number(process.argv[11]),
        innodbDataReads: Number(process.argv[12]),
        innodbDataWrites: Number(process.argv[13]),
      },
      cpu: {
        available: process.argv[14] === "true",
        cpuSeconds: Number(process.argv[15]),
        logicalProcessors: Number(process.argv[16]) || null,
        reason: process.argv[17] || null,
      },
    };
    console.log(JSON.stringify(snapshot));
  ' \
    "$timestamp_epoch_ms" \
    "$(status_value "$status_file" Queries)" \
    "$(status_value "$status_file" Questions)" \
    "$(status_value "$status_file" Com_select)" \
    "$(status_value "$status_file" Threads_connected)" \
    "$(status_value "$status_file" Threads_running)" \
    "$(status_value "$status_file" Connections)" \
    "$(status_value "$status_file" Created_tmp_disk_tables)" \
    "$(status_value "$status_file" Innodb_rows_read)" \
    "$(status_value "$status_file" Innodb_buffer_pool_read_requests)" \
    "$(status_value "$status_file" Innodb_buffer_pool_reads)" \
    "$(status_value "$status_file" Innodb_data_reads)" \
    "$(status_value "$status_file" Innodb_data_writes)" \
    "$cpu_available" \
    "$cpu_seconds" \
    "$logical_processors" \
    "$cpu_reason" \
    >> "$output_file"
}

sample_mysql() {
  while true; do
    collect_snapshot "$SAMPLES_FILE" || true
    sleep "$SAMPLE_INTERVAL_SECONDS"
  done
}

echo "Product detail cache load test 시작"
echo "- scenario: $SCENARIO"
echo "- load: $LOAD_LABEL"
echo "- result: $RESULT_FILE"
echo "- mysql: ${DB_USERNAME_VALUE}@${DB_HOST_VALUE}:${DB_PORT_VALUE}/${DB_NAME_VALUE}"

collect_snapshot "$BEFORE_FILE"
sample_mysql &
SAMPLER_PID="$!"

set +e
SUMMARY_FILE="$K6_SUMMARY_FILE" k6 run "$K6_SCRIPT"
K6_EXIT_CODE="$?"
set -e

if [ -n "$SAMPLER_PID" ] && kill -0 "$SAMPLER_PID" >/dev/null 2>&1; then
  kill "$SAMPLER_PID" >/dev/null 2>&1 || true
  wait "$SAMPLER_PID" >/dev/null 2>&1 || true
  SAMPLER_PID=""
fi

collect_snapshot "$AFTER_FILE"

node "$TEST_DIR/build-result.js" \
  --scenario "$SCENARIO" \
  --timestamp "$LOAD_LABEL" \
  --before "$BEFORE_FILE" \
  --after "$AFTER_FILE" \
  --samples "$SAMPLES_FILE" \
  --k6Summary "$K6_SUMMARY_FILE" \
  --resultFile "$RESULT_FILE" \
  --dbHost "$DB_HOST_VALUE" \
  --dbPort "$DB_PORT_VALUE" \
  --dbName "$DB_NAME_VALUE" \
  --dbUser "$DB_USERNAME_VALUE"

echo "Product detail cache load test 결과 생성 완료"
echo "- result: $RESULT_FILE"

exit "$K6_EXIT_CODE"
