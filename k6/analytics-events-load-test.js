import http from 'k6/http';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'kafka';
const TOTAL_EVENTS = Number(__ENV.TOTAL_EVENTS || 1000000);
const VUS = Number(__ENV.VUS || 1000);
const MAX_DURATION = __ENV.MAX_DURATION || '5m';
const KEYWORD_PREFIX = __ENV.KEYWORD_PREFIX || 'keyword';

const API_PREFIX =
  MODE === 'direct' ? '/api/analytics/benchmark/events/direct' : '/api/analytics/events';

export const success2xx = new Counter('analytics_events_success_2xx');
export const accepted202 = new Counter('analytics_events_accepted_202');
export const created201 = new Counter('analytics_events_created_201');
export const failed = new Counter('analytics_events_failed');
export const searchEvents = new Counter('analytics_events_search');
export const productViewEvents = new Counter('analytics_events_product_view');
export const addToCartEvents = new Counter('analytics_events_add_to_cart');
export const purchaseEvents = new Counter('analytics_events_purchase');

export const options = {
  scenarios: {
    analytics_events: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: TOTAL_EVENTS,
      maxDuration: MAX_DURATION,
    },
  },
  thresholds: {
    http_reqs: [`count==${TOTAL_EVENTS}`],
    analytics_events_success_2xx: [`count==${TOTAL_EVENTS}`],
    analytics_events_failed: ['count==0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

export default function () {
  const sequence = exec.scenario.iterationInTest;
  const eventType = pickEventType(sequence);
  const request = buildRequest(eventType, sequence);
  const response = http.post(`${BASE_URL}${API_PREFIX}${request.path}`, JSON.stringify(request.body), {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint: `POST ${API_PREFIX}${request.path}`,
      mode: MODE,
      event_type: eventType,
    },
  });

  if (response.status >= 200 && response.status < 300) {
    success2xx.add(1);
  } else {
    failed.add(1);
  }

  if (response.status === 202) {
    accepted202.add(1);
  }
  if (response.status === 201) {
    created201.add(1);
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
  const successCount = data.metrics.analytics_events_success_2xx?.values.count ?? 0;
  const summary = {
    mode: MODE,
    target: `${BASE_URL}${API_PREFIX}`,
    totalEvents: TOTAL_EVENTS,
    vus: VUS,
    maxDuration: MAX_DURATION,
    actual: {
      totalRequests: data.metrics.http_reqs?.values.count ?? 0,
      success2xx: successCount,
      accepted202: data.metrics.analytics_events_accepted_202?.values.count ?? 0,
      created201: data.metrics.analytics_events_created_201?.values.count ?? 0,
      failed: data.metrics.analytics_events_failed?.values.count ?? 0,
      avgDurationMs: data.metrics.http_req_duration?.values.avg ?? 0,
      p95DurationMs: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
      testRunDurationMs: durationMs,
      requestThroughputPerSec: durationMs > 0 ? successCount / (durationMs / 1000) : 0,
    },
    eventDistribution: {
      search: data.metrics.analytics_events_search?.values.count ?? 0,
      productView: data.metrics.analytics_events_product_view?.values.count ?? 0,
      addToCart: data.metrics.analytics_events_add_to_cart?.values.count ?? 0,
      purchase: data.metrics.analytics_events_purchase?.values.count ?? 0,
    },
  };

  return {
    [`k6/results/analytics-events-${MODE}-summary.json`]: JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Analytics Events Load Test Summary ===',
      `Mode: ${summary.mode}`,
      `Target: ${summary.target}`,
      `Total events: ${summary.totalEvents}`,
      `VUs: ${summary.vus}`,
      `2xx count: ${summary.actual.success2xx}`,
      `Failed count: ${summary.actual.failed}`,
      `Average duration(ms): ${summary.actual.avgDurationMs.toFixed(2)}`,
      `p95 duration(ms): ${summary.actual.p95DurationMs.toFixed(2)}`,
      `Request throughput(events/sec): ${summary.actual.requestThroughputPerSec.toFixed(2)}`,
      '',
    ].join('\n'),
  };
}
