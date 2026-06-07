import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18081';
const DURATION = __ENV.DURATION || '1m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const USER_ID_MIN = Number(__ENV.USER_ID_MIN || 1);
const USER_ID_RANGE = Number(__ENV.USER_ID_RANGE || 100000);
const PRODUCT_ID_MIN = Number(__ENV.PRODUCT_ID_MIN || 1);
const PRODUCT_ID_RANGE = Number(__ENV.PRODUCT_ID_RANGE || 100000);
const ORDER_ID_MIN = Number(__ENV.ORDER_ID_MIN || 1);
const KEYWORDS = (__ENV.KEYWORDS || 'macbook,keyboard,monitor,mouse,notebook')
  .split(',')
  .map((value) => value.trim())
  .filter((value) => value.length > 0);

const TPS = Number(__ENV.TPS || 1000);
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '3s';
const MAX_VUS = Number(__ENV.MAX_VUS || 6000);

export const acceptedEvents = new Counter('event_pipeline_accepted_events');
export const failedEvents = new Counter('event_pipeline_failed_events');
export const searchAccepted = new Counter('event_pipeline_search_accepted');
export const productViewAccepted = new Counter('event_pipeline_product_view_accepted');
export const addToCartAccepted = new Counter('event_pipeline_add_to_cart_accepted');
export const purchaseAccepted = new Counter('event_pipeline_purchase_accepted');
export const successRate = new Rate('event_pipeline_success_rate');

export const options = {
  scenarios: {
    user_behavior_events: {
      executor: 'constant-arrival-rate',
      rate: TPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    event_pipeline_failed_events: ['count==0'],
    event_pipeline_success_rate: ['rate==1'],
    http_req_duration: ['p(95)<1000', 'p(99)<3000'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

const eventCases = [
  {
    name: 'SEARCH',
    path: '/api/event-pipeline/events/search',
    acceptedCounter: searchAccepted,
    body: (sequence) => ({
      userId: userIdOf(sequence),
      keyword: KEYWORDS[sequence % KEYWORDS.length],
    }),
  },
  {
    name: 'PRODUCT_VIEW',
    path: '/api/event-pipeline/events/product-view',
    acceptedCounter: productViewAccepted,
    body: (sequence) => ({
      userId: userIdOf(sequence),
      productId: productIdOf(sequence),
      searchEventId: sequence + 1,
      searchKeyword: KEYWORDS[sequence % KEYWORDS.length],
    }),
  },
  {
    name: 'ADD_TO_CART',
    path: '/api/event-pipeline/events/add-to-cart',
    acceptedCounter: addToCartAccepted,
    body: (sequence) => ({
      userId: userIdOf(sequence),
      productId: productIdOf(sequence),
    }),
  },
  {
    name: 'PURCHASE',
    path: '/api/event-pipeline/events/purchase',
    acceptedCounter: purchaseAccepted,
    body: (sequence) => ({
      userId: userIdOf(sequence),
      orderId: ORDER_ID_MIN + sequence,
    }),
  },
];

export default function () {
  const sequence = __ITER;
  const eventCase = eventCases[sequence % eventCases.length];
  const response = http.post(
    `${BASE_URL}${eventCase.path}`,
    JSON.stringify(eventCase.body(sequence)),
    {
      timeout: REQUEST_TIMEOUT,
      headers: {
        'Content-Type': 'application/json',
      },
      tags: {
        endpoint: `POST ${eventCase.path}`,
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
  const accepted = data.metrics.event_pipeline_accepted_events?.values.count ?? 0;
  const failed = data.metrics.event_pipeline_failed_events?.values.count ?? 0;
  const successPercent = percentage(accepted, totalRequests);
  const failurePercent = percentage(failed, totalRequests);
  const summary = {
    target: BASE_URL,
    tps: TPS,
    duration: DURATION,
    totalRequests,
    acceptedEvents: accepted,
    failedEvents: failed,
    successRatePercent: successPercent,
    failureRatePercent: failurePercent,
    byEventType: {
      searchAccepted: data.metrics.event_pipeline_search_accepted?.values.count ?? 0,
      productViewAccepted: data.metrics.event_pipeline_product_view_accepted?.values.count ?? 0,
      addToCartAccepted: data.metrics.event_pipeline_add_to_cart_accepted?.values.count ?? 0,
      purchaseAccepted: data.metrics.event_pipeline_purchase_accepted?.values.count ?? 0,
    },
    latencyMs: {
      avg: data.metrics.http_req_duration?.values.avg ?? 0,
      p95: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      p99: data.metrics.http_req_duration?.values['p(99)'] ?? 0,
      max: data.metrics.http_req_duration?.values.max ?? 0,
    },
  };

  return {
    'k6/results/event-pipeline-user-behavior-events-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Event Pipeline Load Test Summary ===',
      `Target: ${summary.target}`,
      `TPS: ${summary.tps}`,
      `Duration: ${summary.duration}`,
      `Total requests: ${summary.totalRequests}`,
      `Accepted events: ${summary.acceptedEvents}`,
      `Failed events: ${summary.failedEvents}`,
      `API success rate: ${summary.successRatePercent.toFixed(2)}%`,
      `API failure rate: ${summary.failureRatePercent.toFixed(2)}%`,
      `Latency avg(ms): ${summary.latencyMs.avg.toFixed(2)}`,
      `Latency p95(ms): ${summary.latencyMs.p95.toFixed(2)}`,
      `Latency p99(ms): ${summary.latencyMs.p99.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}

function userIdOf(sequence) {
  return USER_ID_MIN + (sequence % USER_ID_RANGE);
}

function productIdOf(sequence) {
  return PRODUCT_ID_MIN + (sequence % PRODUCT_ID_RANGE);
}

function percentage(part, whole) {
  if (whole === 0) {
    return 0;
  }
  return (part / whole) * 100;
}
