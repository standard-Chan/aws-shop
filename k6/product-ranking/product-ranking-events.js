import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18083';
const DURATION = __ENV.DURATION || '1m';
const USER_ID_MIN = Number(__ENV.USER_ID_MIN || 1);
const USER_ID_RANGE = Number(__ENV.USER_ID_RANGE || 100000);
const PRODUCT_ID_MIN = Number(__ENV.PRODUCT_ID_MIN || 600000);
const PRODUCT_ID_RANGE = Number(__ENV.PRODUCT_ID_RANGE || 1000000);
const EVENT_ID_MIN = Number(__ENV.EVENT_ID_MIN || 1);
const KEYWORDS = (__ENV.KEYWORDS || 'macbook,keyboard,monitor,mouse,notebook')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);

const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 5000);
const TPS = Number(__ENV.TPS || 5000);
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '3s';
const MAX_VUS = Number(__ENV.MAX_VUS || 20000);

const EVENT_PATH = '/api/event-pipeline/product-ranking/events';

export const acceptedEvents = new Counter('product_ranking_accepted_events');
export const failedEvents = new Counter('product_ranking_failed_events');
export const productViewAccepted = new Counter('product_ranking_product_view_accepted');
export const addToCartAccepted = new Counter('product_ranking_add_to_cart_accepted');
export const successRate = new Rate('product_ranking_success_rate');

export const options = {
  scenarios: {
    product_ranking_events: {
      executor: 'constant-arrival-rate',
      rate: TPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    product_ranking_failed_events: ['count==0'],
    product_ranking_success_rate: ['rate==1'],
    http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

const eventCases = [
  {
    name: 'PRODUCT_VIEW',
    acceptedCounter: productViewAccepted,
    body: (sequence) => ({
      eventId: eventIdOf(sequence),
      eventType: 'PRODUCT_VIEW',
      userId: userIdOf(sequence),
      occurredAt: occurredAtOf(sequence),
      keyword: KEYWORDS[sequence % KEYWORDS.length],
      productId: productIdOf(sequence),
      orderId: null,
      searchEventId: eventIdOf(sequence) - 1,
    }),
  },
  {
    name: 'ADD_TO_CART',
    acceptedCounter: addToCartAccepted,
    body: (sequence) => ({
      eventId: eventIdOf(sequence),
      eventType: 'ADD_TO_CART',
      userId: userIdOf(sequence),
      occurredAt: occurredAtOf(sequence),
      keyword: null,
      productId: productIdOf(sequence),
      orderId: null,
      searchEventId: null,
    }),
  },
];

export default function () {
  const sequence = exec.scenario.iterationInTest;
  const eventCase = eventCases[sequence % eventCases.length];
  const response = http.post(
    `${BASE_URL}${EVENT_PATH}`,
    JSON.stringify(eventCase.body(sequence)),
    {
      timeout: REQUEST_TIMEOUT,
      headers: {
        'Content-Type': 'application/json',
      },
      tags: {
        endpoint: `POST ${EVENT_PATH}`,
        event_type: eventCase.name,
      },
    }
  );

  const accepted = response.status === 202;
  successRate.add(accepted);

  if (accepted) {
    acceptedEvents.add(1);
    eventCase.acceptedCounter.add(1);
  } else {
    failedEvents.add(1);
  }

  check(response, {
    'status is 202': (res) => res.status === 202,
  });
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs?.values.count ?? 0;
  const accepted = data.metrics.product_ranking_accepted_events?.values.count ?? 0;
  const failed = data.metrics.product_ranking_failed_events?.values.count ?? 0;
  const successPercent = percentage(accepted, totalRequests);
  const failurePercent = percentage(failed, totalRequests);
  const summary = {
    target: BASE_URL,
    endpoint: EVENT_PATH,
    tps: TPS,
    duration: DURATION,
    totalRequests,
    acceptedEvents: accepted,
    failedEvents: failed,
    successRatePercent: successPercent,
    failureRatePercent: failurePercent,
    byEventType: {
      productViewAccepted: data.metrics.product_ranking_product_view_accepted?.values.count ?? 0,
      addToCartAccepted: data.metrics.product_ranking_add_to_cart_accepted?.values.count ?? 0,
    },
    latencyMs: {
      avg: data.metrics.http_req_duration?.values.avg ?? 0,
      p95: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      p99: data.metrics.http_req_duration?.values['p(99)'] ?? 0,
      max: data.metrics.http_req_duration?.values.max ?? 0,
    },
  };

  return {
    'k6/results/product-ranking-events-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Product Ranking Load Test Summary ===',
      `Target: ${summary.target}`,
      `Endpoint: ${summary.endpoint}`,
      `TPS: ${summary.tps}`,
      `Duration: ${summary.duration}`,
      `Total requests: ${summary.totalRequests}`,
      `Accepted events: ${summary.acceptedEvents}`,
      `Failed events: ${summary.failedEvents}`,
      `API success rate: ${summary.successRatePercent.toFixed(2)}%`,
      `API failure rate: ${summary.failureRatePercent.toFixed(2)}%`,
      `Product view accepted: ${summary.byEventType.productViewAccepted}`,
      `Add to cart accepted: ${summary.byEventType.addToCartAccepted}`,
      `Latency avg(ms): ${summary.latencyMs.avg.toFixed(2)}`,
      `Latency p95(ms): ${summary.latencyMs.p95.toFixed(2)}`,
      `Latency p99(ms): ${summary.latencyMs.p99.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}

function eventIdOf(sequence) {
  return EVENT_ID_MIN + sequence;
}

function userIdOf(sequence) {
  return USER_ID_MIN + (sequence % USER_ID_RANGE);
}

function productIdOf(sequence) {
  return PRODUCT_ID_MIN + (sequence % PRODUCT_ID_RANGE);
}

function occurredAtOf(sequence) {
  return new Date(Date.now() + sequence).toISOString();
}

function percentage(part, whole) {
  if (whole === 0) {
    return 0;
  }
  return (part / whole) * 100;
}
