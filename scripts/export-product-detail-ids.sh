#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
OUTPUT_FILE="${OUTPUT_FILE:-$ROOT_DIR/k6/results/product-detail-ids.csv}"
LIMIT="${LIMIT:-500000}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql CLI가 필요합니다. mysql client를 설치한 뒤 다시 실행하세요." >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo ".env 파일을 찾을 수 없습니다: $ENV_FILE" >&2
  exit 1
fi

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

if ! [[ "$LIMIT" =~ ^[0-9]+$ ]] || [ "$LIMIT" -le 0 ]; then
  echo "LIMIT은 양의 정수여야 합니다: $LIMIT" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

echo "Product ID 추출 시작"
echo "- env: $ENV_FILE"
echo "- db: ${DB_USERNAME_VALUE}@${DB_HOST_VALUE}:${DB_PORT_VALUE}/${DB_NAME_VALUE}"
echo "- limit: $LIMIT"
echo "- output: $OUTPUT_FILE"

MYSQL_PWD="$DB_PASSWORD_VALUE" mysql \
  --protocol=TCP \
  --host="$DB_HOST_VALUE" \
  --port="$DB_PORT_VALUE" \
  --user="$DB_USERNAME_VALUE" \
  --database="$DB_NAME_VALUE" \
  --batch \
  --raw \
  --skip-column-names \
  --quick \
  --execute="SELECT id FROM product ORDER BY id ASC LIMIT ${LIMIT};" \
  > "$OUTPUT_FILE"

LINE_COUNT="$(wc -l < "$OUTPUT_FILE" | tr -d ' ')"

echo "Product ID 추출 완료"
echo "- rows: $LINE_COUNT"
echo "- sample:"
sed -n '1,5p' "$OUTPUT_FILE"

if [ "$LINE_COUNT" -ne "$LIMIT" ]; then
  echo "경고: 요청한 LIMIT($LIMIT)과 추출 row 수($LINE_COUNT)가 다릅니다." >&2
fi
