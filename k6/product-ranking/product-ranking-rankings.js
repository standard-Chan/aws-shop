/*
 * Product Ranking 조회 부하 테스트
 *
 * 실행 전 product-ranking 서버와 Redis를 실행한다.
 *   docker compose -f event-pipeline/docker/compose-event-pipeline-local.yml up -d redis
 *   ./gradlew :event-pipeline:product-ranking:bootRun
 *
 * 단일 window 조회:
 *   TPS=1000 DURATION=1m WINDOW=ONE_HOUR LIMIT=10 k6 run k6/product-ranking/product-ranking-rankings.js
 *   TPS=1000 DURATION=1m WINDOW=ONE_DAY LIMIT=10 k6 run k6/product-ranking/product-ranking-rankings.js
 *   TPS=1000 DURATION=1m WINDOW=ONE_WEEK LIMIT=10 k6 run k6/product-ranking/product-ranking-rankings.js
 *
 * 여러 window를 번갈아 조회:
 *   TPS=1000 DURATION=1m WINDOWS=ONE_HOUR,ONE_DAY,ONE_WEEK LIMIT=10 k6 run k6/product-ranking/product-ranking-rankings.js
 *
 * 주요 환경 변수:
 *   BASE_URL=http://localhost:18083
 *   TPS=1000
 *   DURATION=1m
 *   WINDOW=ONE_HOUR
 *   WINDOWS=ONE_HOUR,ONE_DAY,ONE_WEEK
 *   LIMIT=10
 *   REQUEST_TIMEOUT=3s
 *   PRE_ALLOCATED_VUS=1000
 *   MAX_VUS=10000
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18083';
const DURATION = __ENV.DURATION || '1m';
const TPS = Number(__ENV.TPS || 1000);
const LIMIT = Number(__ENV.LIMIT || 10);
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '10s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(TPS, 100));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(TPS * 2, PRE_ALLOCATED_VUS));
const RANKINGS_PATH = '/api/event-pipeline/product-ranking/rankings';
const ALLOWED_WINDOWS = ['ONE_HOUR', 'ONE_DAY', 'ONE_WEEK'];
const WINDOWS = selectedWindows();

export const successfulRankingRequests = new Counter('product_ranking_successful_ranking_requests');
export const failedRankingRequests = new Counter('product_ranking_failed_ranking_requests');
export const oneHourRequests = new Counter('product_ranking_one_hour_requests');
export const oneDayRequests = new Counter('product_ranking_one_day_requests');
export const oneWeekRequests = new Counter('product_ranking_one_week_requests');
export const successRate = new Rate('product_ranking_ranking_success_rate');

export const options = {
  scenarios: {
    product_ranking_rankings: {
      executor: 'constant-arrival-rate',
      rate: TPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    product_ranking_failed_ranking_requests: ['count==0'],
    product_ranking_ranking_success_rate: ['rate==1'],
    http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const sequence = exec.scenario.iterationInTest;
  const window = WINDOWS[sequence % WINDOWS.length];
  const url = `${BASE_URL}${RANKINGS_PATH}?window=${window}&limit=${LIMIT}`;
  const response = http.get(url, {
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint: `GET ${RANKINGS_PATH}`,
      ranking_window: window,
    },
  });

  const success = response.status === 200;
  successRate.add(success);
  countByWindow(window);

  if (success) {
    successfulRankingRequests.add(1);
  } else {
    failedRankingRequests.add(1);
  }

  check(response, {
    'status is 200': (res) => res.status === 200,
    'response is array': (res) => {
      try {
        return Array.isArray(JSON.parse(res.body));
      } catch (ignored) {
        return false;
      }
    },
  });
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs?.values.count ?? 0;
  const successful = data.metrics.product_ranking_successful_ranking_requests?.values.count ?? 0;
  const failed = data.metrics.product_ranking_failed_ranking_requests?.values.count ?? 0;
  const summary = {
    target: BASE_URL,
    endpoint: RANKINGS_PATH,
    windows: WINDOWS,
    limit: LIMIT,
    tps: TPS,
    duration: DURATION,
    totalRequests,
    successfulRankingRequests: successful,
    failedRankingRequests: failed,
    successRatePercent: percentage(successful, totalRequests),
    failureRatePercent: percentage(failed, totalRequests),
    byWindow: {
      oneHourRequests: data.metrics.product_ranking_one_hour_requests?.values.count ?? 0,
      oneDayRequests: data.metrics.product_ranking_one_day_requests?.values.count ?? 0,
      oneWeekRequests: data.metrics.product_ranking_one_week_requests?.values.count ?? 0,
    },
    latencyMs: {
      avg: data.metrics.http_req_duration?.values.avg ?? 0,
      p95: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      p99: data.metrics.http_req_duration?.values['p(99)'] ?? 0,
      max: data.metrics.http_req_duration?.values.max ?? 0,
    },
  };

  return {
    'k6/results/product-ranking-rankings-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Product Ranking Query Load Test Summary ===',
      `Target: ${summary.target}`,
      `Endpoint: ${summary.endpoint}`,
      `Windows: ${summary.windows.join(',')}`,
      `Limit: ${summary.limit}`,
      `TPS: ${summary.tps}`,
      `Duration: ${summary.duration}`,
      `Total requests: ${summary.totalRequests}`,
      `Successful ranking requests: ${summary.successfulRankingRequests}`,
      `Failed ranking requests: ${summary.failedRankingRequests}`,
      `API success rate: ${summary.successRatePercent.toFixed(2)}%`,
      `API failure rate: ${summary.failureRatePercent.toFixed(2)}%`,
      `ONE_HOUR requests: ${summary.byWindow.oneHourRequests}`,
      `ONE_DAY requests: ${summary.byWindow.oneDayRequests}`,
      `ONE_WEEK requests: ${summary.byWindow.oneWeekRequests}`,
      `Latency avg(ms): ${summary.latencyMs.avg.toFixed(2)}`,
      `Latency p95(ms): ${summary.latencyMs.p95.toFixed(2)}`,
      `Latency p99(ms): ${summary.latencyMs.p99.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}

function selectedWindows() {
  const rawValue = __ENV.WINDOWS || __ENV.WINDOW || 'ONE_HOUR';
  const windows = rawValue
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);

  for (const window of windows) {
    if (!ALLOWED_WINDOWS.includes(window)) {
      throw new Error(`지원하지 않는 WINDOW 값입니다: ${window}`);
    }
  }
  return windows;
}

function countByWindow(window) {
  if (window === 'ONE_HOUR') {
    oneHourRequests.add(1);
    return;
  }
  if (window === 'ONE_DAY') {
    oneDayRequests.add(1);
    return;
  }
  oneWeekRequests.add(1);
}

function percentage(part, whole) {
  if (whole === 0) {
    return 0;
  }
  return (part / whole) * 100;
}
