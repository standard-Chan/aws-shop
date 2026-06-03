import http from 'k6/http';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_EVENTS = Number(__ENV.TOTAL_EVENTS || 100000);
const VUS = Number(__ENV.VUS || 100);
const BATCH_SIZE = Number(__ENV.BATCH_SIZE || 100);
const MAX_DURATION = __ENV.MAX_DURATION || '5m';
const KEYWORD_PREFIX = __ENV.KEYWORD_PREFIX || 'keyword';
const API_PATH = '/api/analytics/events/batch';
const TOTAL_REQUESTS = Math.ceil(TOTAL_EVENTS / BATCH_SIZE);

export const success2xx = new Counter('analytics_batch_success_2xx');
export const accepted202 = new Counter('analytics_batch_accepted_202');
export const failed = new Counter('analytics_batch_failed');
export const acceptedEvents = new Counter('analytics_batch_events_accepted');
export const searchEvents = new Counter('analytics_batch_events_search');
export const productViewEvents = new Counter('analytics_batch_events_product_view');
export const addToCartEvents = new Counter('analytics_batch_events_add_to_cart');
export const purchaseEvents = new Counter('analytics_batch_events_purchase');

export const options = {
  scenarios: {
    analytics_batch_events: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: TOTAL_REQUESTS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_reqs: [`count==${TOTAL_REQUESTS}`],
    analytics_batch_events_accepted: [`count==${TOTAL_EVENTS}`],
    analytics_batch_failed: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

export default function () {
  const batchIndex = exec.scenario.iterationInTest;
  const startSequence = batchIndex * BATCH_SIZE;
  const count = Math.min(BATCH_SIZE, TOTAL_EVENTS - startSequence);
  const events = [];
  const distribution = {
    search: 0,
    productView: 0,
    addToCart: 0,
    purchase: 0,
  };

  for (let offset = 0; offset < count; offset += 1) {
    const sequence = startSequence + offset;
    const eventType = pickEventType(sequence);
    events.push(buildEvent(eventType, sequence));
    incrementDistribution(distribution, eventType);
  }

  const response = http.post(`${BASE_URL}${API_PATH}`, JSON.stringify({ events }), {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint: `POST ${API_PATH}`,
      mode: 'kafka-batch-api',
    },
  });

  if (response.status >= 200 && response.status < 300) {
    success2xx.add(1);
    acceptedEvents.add(count);
  } else {
    failed.add(1);
  }

  if (response.status === 202) {
    accepted202.add(1);
  }

  searchEvents.add(distribution.search);
  productViewEvents.add(distribution.productView);
  addToCartEvents.add(distribution.addToCart);
  purchaseEvents.add(distribution.purchase);
}

function pickEventType(sequence) {
  const bucket = sequence % 100;
  if (bucket < 40) {
    return 'SEARCH';
  }
  if (bucket < 75) {
    return 'PRODUCT_VIEW';
  }
  if (bucket < 95) {
    return 'ADD_TO_CART';
  }
  return 'PURCHASE';
}

function buildEvent(eventType, sequence) {
  const userId = (sequence % 10000) + 1;
  const productId = (sequence % 5000) + 1;
  const keyword = `${KEYWORD_PREFIX}-${sequence % 1000}`;

  if (eventType === 'SEARCH') {
    return {
      eventType,
      userId,
      keyword,
    };
  }

  if (eventType === 'PRODUCT_VIEW') {
    return {
      eventType,
      userId,
      productId,
      searchEventId: sequence + 1,
      keyword,
    };
  }

  if (eventType === 'ADD_TO_CART') {
    return {
      eventType,
      userId,
      productId,
    };
  }

  return {
    eventType,
    userId,
    orderId: sequence + 1,
  };
}

function incrementDistribution(distribution, eventType) {
  if (eventType === 'SEARCH') {
    distribution.search += 1;
  } else if (eventType === 'PRODUCT_VIEW') {
    distribution.productView += 1;
  } else if (eventType === 'ADD_TO_CART') {
    distribution.addToCart += 1;
  } else {
    distribution.purchase += 1;
  }
}

export function handleSummary(data) {
  const durationMs = data.state.testRunDurationMs || 0;
  const successCount = data.metrics.analytics_batch_success_2xx?.values.count ?? 0;
  const acceptedEventCount = data.metrics.analytics_batch_events_accepted?.values.count ?? 0;
  const summary = {
    mode: 'kafka-batch-api',
    target: `${BASE_URL}${API_PATH}`,
    totalEvents: TOTAL_EVENTS,
    totalRequests: TOTAL_REQUESTS,
    batchSize: BATCH_SIZE,
    vus: VUS,
    maxDuration: MAX_DURATION,
    actual: {
      totalHttpRequests: data.metrics.http_reqs?.values.count ?? 0,
      success2xx: successCount,
      accepted202: data.metrics.analytics_batch_accepted_202?.values.count ?? 0,
      failed: data.metrics.analytics_batch_failed?.values.count ?? 0,
      acceptedEvents: acceptedEventCount,
      avgDurationMs: data.metrics.http_req_duration?.values.avg ?? 0,
      p95DurationMs: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      testRunDurationMs: durationMs,
      requestThroughputPerSec: durationMs > 0 ? successCount / (durationMs / 1000) : 0,
      eventThroughputPerSec: durationMs > 0 ? acceptedEventCount / (durationMs / 1000) : 0,
    },
    eventDistribution: {
      search: data.metrics.analytics_batch_events_search?.values.count ?? 0,
      productView: data.metrics.analytics_batch_events_product_view?.values.count ?? 0,
      addToCart: data.metrics.analytics_batch_events_add_to_cart?.values.count ?? 0,
      purchase: data.metrics.analytics_batch_events_purchase?.values.count ?? 0,
    },
  };

  return {
    'k6/results/analytics-events-kafka-batch-api-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Analytics Batch Events Load Test Summary ===',
      `Target: ${summary.target}`,
      `Total events: ${summary.totalEvents}`,
      `Total HTTP requests: ${summary.totalRequests}`,
      `Batch size: ${summary.batchSize}`,
      `VUs: ${summary.vus}`,
      `2xx request count: ${summary.actual.success2xx}`,
      `Accepted event count: ${summary.actual.acceptedEvents}`,
      `Failed request count: ${summary.actual.failed}`,
      `Average duration(ms): ${summary.actual.avgDurationMs.toFixed(2)}`,
      `p95 duration(ms): ${summary.actual.p95DurationMs.toFixed(2)}`,
      `Request throughput(req/sec): ${summary.actual.requestThroughputPerSec.toFixed(2)}`,
      `Event throughput(events/sec): ${summary.actual.eventThroughputPerSec.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}
