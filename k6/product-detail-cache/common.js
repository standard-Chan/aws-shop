import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const DURATION = __ENV.DURATION || '1m';
export const TPS = Number(__ENV.TPS || 100);
export const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '10s';
export const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(TPS, 100));
export const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(TPS * 2, PRE_ALLOCATED_VUS));
export const PRODUCT_IDS_FILE = __ENV.PRODUCT_IDS_FILE || './ids/product-ids.csv';
export const SEED = Number(__ENV.SEED || 20260717);
export const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 1000);
export const P99_THRESHOLD_MS = Number(__ENV.P99_THRESHOLD_MS || 3000);
export const PRODUCT_DETAIL_PATH_PREFIX = '/api/products/';

export const successfulRequests = new Counter('product_detail_success_200');
export const notFoundRequests = new Counter('product_detail_not_found_404');
export const failedRequests = new Counter('product_detail_failed_requests');
export const successRate = new Rate('product_detail_success_rate');

export const productIds = new SharedArray('product-detail-cache-product-ids', () => {
  const ids = open(PRODUCT_IDS_FILE)
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter((value) => value.length > 0);

  if (ids.length === 0) {
    throw new Error(`Product ID 파일이 비어 있습니다: ${PRODUCT_IDS_FILE}`);
  }

  return ids;
});

const permutationMultiplier = coprimeMultiplier(productIds.length, 104729);
const permutationOffset = positiveModulo(SEED, productIds.length);

export function createProductDetailOptions(scenarioName) {
  return {
    scenarios: {
      [scenarioName]: {
        executor: 'constant-arrival-rate',
        rate: TPS,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: PRE_ALLOCATED_VUS,
        maxVUs: MAX_VUS,
      },
    },
    thresholds: {
      product_detail_failed_requests: ['count==0'],
      product_detail_success_rate: ['rate==1'],
      http_req_duration: [`p(95)<${P95_THRESHOLD_MS}`, `p(99)<${P99_THRESHOLD_MS}`],
      dropped_iterations: ['count==0'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  };
}

export function uniformProductId() {
  const sequence = exec.scenario.iterationInTest;
  const index = Math.floor(randomUnit(sequence, 1) * productIds.length);
  return productIds[index];
}

export function zipfProductId(alpha) {
  const sequence = exec.scenario.iterationInTest;
  const rank = zipfRank(productIds.length, alpha, randomUnit(sequence, 2));
  const index = permutedIndex(rank - 1);
  return productIds[index];
}

export function requestProductDetail(productId, scenarioName, distributionName) {
  const path = `${PRODUCT_DETAIL_PATH_PREFIX}${productId}`;
  const response = http.get(`${BASE_URL}${path}`, {
    timeout: REQUEST_TIMEOUT,
    tags: {
      endpoint: `GET ${PRODUCT_DETAIL_PATH_PREFIX}{id}`,
      scenario: scenarioName,
      distribution: distributionName,
    },
  });

  const success = response.status === 200;
  successRate.add(success);

  if (success) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
    if (response.status === 404) {
      notFoundRequests.add(1);
    }
  }

  check(response, {
    'status is 200': (res) => res.status === 200,
  });
}

export function productDetailSummary(data, scenarioName, distributionName, summaryFile) {
  const totalRequests = data.metrics.http_reqs?.values.count ?? 0;
  const successful = data.metrics.product_detail_success_200?.values.count ?? 0;
  const notFound = data.metrics.product_detail_not_found_404?.values.count ?? 0;
  const failed = data.metrics.product_detail_failed_requests?.values.count ?? 0;
  const summary = {
    target: BASE_URL,
    endpoint: `GET ${PRODUCT_DETAIL_PATH_PREFIX}{id}`,
    scenario: scenarioName,
    distribution: distributionName,
    productIdFile: PRODUCT_IDS_FILE,
    productIdCount: productIds.length,
    seed: SEED,
    tps: TPS,
    duration: DURATION,
    totalRequests,
    successfulRequests: successful,
    notFoundRequests: notFound,
    failedRequests: failed,
    successRatePercent: percentage(successful, totalRequests),
    failureRatePercent: percentage(failed, totalRequests),
    latencyMs: {
      avg: data.metrics.http_req_duration?.values.avg ?? 0,
      p95: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      p99: data.metrics.http_req_duration?.values['p(99)'] ?? 0,
      max: data.metrics.http_req_duration?.values.max ?? 0,
    },
  };

  return {
    [summaryFile]: JSON.stringify(summary, null, 2),
    stdout: [
      '',
      `=== Product Detail Cache Load Test Summary: ${scenarioName} ===`,
      `Target: ${summary.target}`,
      `Endpoint: ${summary.endpoint}`,
      `Distribution: ${summary.distribution}`,
      `Product ID file: ${summary.productIdFile}`,
      `Product ID count: ${summary.productIdCount}`,
      `Seed: ${summary.seed}`,
      `TPS: ${summary.tps}`,
      `Duration: ${summary.duration}`,
      `Total requests: ${summary.totalRequests}`,
      `Successful requests: ${summary.successfulRequests}`,
      `404 requests: ${summary.notFoundRequests}`,
      `Failed requests: ${summary.failedRequests}`,
      `API success rate: ${summary.successRatePercent.toFixed(2)}%`,
      `API failure rate: ${summary.failureRatePercent.toFixed(2)}%`,
      `Latency avg(ms): ${summary.latencyMs.avg.toFixed(2)}`,
      `Latency p95(ms): ${summary.latencyMs.p95.toFixed(2)}`,
      `Latency p99(ms): ${summary.latencyMs.p99.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}

function zipfRank(size, alpha, unit) {
  const u = Math.min(Math.max(unit, Number.EPSILON), 1 - Number.EPSILON);

  if (alpha === 1) {
    return clampRank(Math.floor(Math.exp(u * Math.log(size))), size);
  }

  const oneMinusAlpha = 1 - alpha;
  const maxPower = Math.pow(size, oneMinusAlpha);
  const rankPower = 1 + u * (maxPower - 1);
  return clampRank(Math.floor(Math.pow(rankPower, 1 / oneMinusAlpha)), size);
}

function permutedIndex(rankZeroBased) {
  return positiveModulo((rankZeroBased * permutationMultiplier) + permutationOffset, productIds.length);
}

function randomUnit(sequence, salt) {
  let value = (SEED + Math.imul(sequence + 1, 0x9e3779b1) + Math.imul(salt, 0x85ebca6b)) >>> 0;
  value = Math.imul(value ^ (value >>> 16), 0x7feb352d) >>> 0;
  value = Math.imul(value ^ (value >>> 15), 0x846ca68b) >>> 0;
  return ((value ^ (value >>> 16)) >>> 0) / 4294967296;
}

function coprimeMultiplier(size, start) {
  let candidate = positiveModulo(start, size);
  if (candidate % 2 === 0) {
    candidate += 1;
  }
  while (gcd(candidate, size) !== 1) {
    candidate += 2;
  }
  return candidate;
}

function gcd(left, right) {
  let a = Math.abs(left);
  let b = Math.abs(right);
  while (b !== 0) {
    const next = a % b;
    a = b;
    b = next;
  }
  return a;
}

function positiveModulo(value, divisor) {
  return ((value % divisor) + divisor) % divisor;
}

function clampRank(rank, size) {
  if (rank < 1) {
    return 1;
  }
  if (rank > size) {
    return size;
  }
  return rank;
}

function percentage(part, whole) {
  if (whole === 0) {
    return 0;
  }
  return (part / whole) * 100;
}
