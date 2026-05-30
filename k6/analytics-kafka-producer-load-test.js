import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_EVENTS = Number(__ENV.TOTAL_EVENTS || 100000);
const VUS = Number(__ENV.VUS || 100);
const MAX_DURATION = __ENV.MAX_DURATION || '5m';
const KEYWORD_PREFIX = __ENV.KEYWORD_PREFIX || 'kafka-keyword';

export const accepted202 = new Counter('analytics_kafka_accepted_202');
export const failed = new Counter('analytics_kafka_failed');
export const searchEvents = new Counter('analytics_kafka_search');
export const productViewEvents = new Counter('analytics_kafka_product_view');
export const addToCartEvents = new Counter('analytics_kafka_add_to_cart');
export const purchaseEvents = new Counter('analytics_kafka_purchase');

export const options = {
  scenarios: {
    kafka_analytics_events: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: TOTAL_EVENTS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_reqs: [`count==${TOTAL_EVENTS}`],
    analytics_kafka_accepted_202: [`count==${TOTAL_EVENTS}`],
    analytics_kafka_failed: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const sequence = exec.scenario.iterationInTest;
  const eventType = pickEventType(sequence);
  const request = buildRequest(eventType, sequence);
  const response = http.post(`${BASE_URL}/api/analytics/events${request.path}`, JSON.stringify(request.body), {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      target: 'analytics-kafka-producer',
      event_type: eventType,
      endpoint: `POST /api/analytics/events${request.path}`,
    },
  });

  const ok = check(response, {
    'status is 202': (res) => res.status === 202,
  });

  if (ok) {
    accepted202.add(1);
  } else {
    failed.add(1);
  }

  incrementEventCounter(eventType);
}

function pickEventType(sequence) {
  const bucket = sequence % 100;
  if (bucket < 40) {
    return 'search';
  }
  if (bucket < 75) {
    return 'product-view';
  }
  if (bucket < 95) {
    return 'add-to-cart';
  }
  return 'purchase';
}

function buildRequest(eventType, sequence) {
  const userId = (sequence % 10000) + 1;
  const productId = (sequence % 5000) + 1;
  const keyword = `${KEYWORD_PREFIX}-${sequence % 1000}`;

  if (eventType === 'search') {
    return {
      path: '/search',
      body: {
        userId,
        keyword,
      },
    };
  }

  if (eventType === 'product-view') {
    return {
      path: '/product-view',
      body: {
        userId,
        productId,
        searchEventId: sequence + 1,
        searchKeyword: keyword,
      },
    };
  }

  if (eventType === 'add-to-cart') {
    return {
      path: '/add-to-cart',
      body: {
        userId,
        productId,
      },
    };
  }

  return {
    path: '/purchase',
    body: {
      userId,
      orderId: sequence + 1,
    },
  };
}

function incrementEventCounter(eventType) {
  if (eventType === 'search') {
    searchEvents.add(1);
  } else if (eventType === 'product-view') {
    productViewEvents.add(1);
  } else if (eventType === 'add-to-cart') {
    addToCartEvents.add(1);
  } else {
    purchaseEvents.add(1);
  }
}

export function handleSummary(data) {
  const durationMs = data.state.testRunDurationMs || 0;
  const acceptedCount = data.metrics.analytics_kafka_accepted_202?.values.count ?? 0;
  const summary = {
    target: `${BASE_URL}/api/analytics/events`,
    totalEvents: TOTAL_EVENTS,
    vus: VUS,
    maxDuration: MAX_DURATION,
    actual: {
      totalRequests: data.metrics.http_reqs?.values.count ?? 0,
      accepted202: acceptedCount,
      failed: data.metrics.analytics_kafka_failed?.values.count ?? 0,
      avgDurationMs: data.metrics.http_req_duration?.values.avg ?? 0,
      p95DurationMs: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      p99DurationMs: data.metrics.http_req_duration?.values['p(99)'] ?? 0,
      testRunDurationMs: durationMs,
      acceptedThroughputPerSec: durationMs > 0 ? acceptedCount / (durationMs / 1000) : 0,
    },
    eventDistribution: {
      search: data.metrics.analytics_kafka_search?.values.count ?? 0,
      productView: data.metrics.analytics_kafka_product_view?.values.count ?? 0,
      addToCart: data.metrics.analytics_kafka_add_to_cart?.values.count ?? 0,
      purchase: data.metrics.analytics_kafka_purchase?.values.count ?? 0,
    },
  };

  return {
    'k6/results/analytics-kafka-producer-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Analytics Kafka Producer Load Test Summary ===',
      `Target: ${summary.target}`,
      `Total events: ${summary.totalEvents}`,
      `VUs: ${summary.vus}`,
      `202 accepted count: ${summary.actual.accepted202}`,
      `Failed count: ${summary.actual.failed}`,
      `Average duration(ms): ${summary.actual.avgDurationMs.toFixed(2)}`,
      `p95 duration(ms): ${summary.actual.p95DurationMs.toFixed(2)}`,
      `p99 duration(ms): ${summary.actual.p99DurationMs.toFixed(2)}`,
      `Accepted throughput(events/sec): ${summary.actual.acceptedThroughputPerSec.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}
