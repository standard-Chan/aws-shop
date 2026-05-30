#!/usr/bin/env bash

# ./scripts/run-analytics-mysql-10mb.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

export ANALYTICS_MYSQL_PORT="${ANALYTICS_MYSQL_PORT:-3307}"
export ANALYTICS_MYSQL_ROOT_PASSWORD="${ANALYTICS_MYSQL_ROOT_PASSWORD:-root}"
export DB_WRITE_LIMIT_DEVICE="${DB_WRITE_LIMIT_DEVICE:-/dev/sdb}"
export DB_WRITE_LIMIT_BPS="${DB_WRITE_LIMIT_BPS:-10mb}"

echo "Starting analytics MySQL for load tests"
echo "database: aws_shop_test"
echo "port: ${ANALYTICS_MYSQL_PORT}"
echo "write limit: ${DB_WRITE_LIMIT_BPS} on ${DB_WRITE_LIMIT_DEVICE}"

docker compose \
  -f "${ROOT_DIR}/docker/analytics-mysql-10mb-compose.yml" \
  up -d analytics-mysql

cat <<EOF

Spring Boot env for this DB:

DB=mysql
DB_HOST=localhost
DB_PORT=${ANALYTICS_MYSQL_PORT}
DB_NAME=aws_shop_test
DB_SETTINGS=serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true
DB_USERNAME=root
DB_PASSWORD=${ANALYTICS_MYSQL_ROOT_PASSWORD}

If the write limit is not applied, check the host block device and rerun with:
DB_WRITE_LIMIT_DEVICE=/dev/<device> ${BASH_SOURCE[0]}
EOF
