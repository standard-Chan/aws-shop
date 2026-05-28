import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { check } from 'k6';

// 실행 : k6 run k6/payment-create-duplicate-guard.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORDER_IDS = (__ENV.ORDER_IDS || '1,2,3,4,5')
.split(',')
.map((value) => Number(value.trim()))
.filter((value) => Number.isFinite(value));
const VUS = Number(__ENV.VUS || 50);
const ROUND_GAP_SECONDS = Number(__ENV.ROUND_GAP_SECONDS || 5);
const EXPECTED_SUCCESS = Number(__ENV.EXPECTED_SUCCESS || 1);
const EXPECTED_CONFLICT = Number(__ENV.EXPECTED_CONFLICT || VUS - EXPECTED_SUCCESS);
const EXPECTED_SERVER_ERROR = Number(__ENV.EXPECTED_SERVER_ERROR || 0);
const TOTAL_EXPECTED_REQUESTS = ORDER_IDS.length * VUS;

export const success200 = new Counter('payment_create_success_200');
export const conflict409 = new Counter('payment_create_conflict_409');
export const serverError500 = new Counter('payment_create_server_error_500');
export const unexpectedStatus = new Counter('payment_create_unexpected_status');
export const order1Success200 = new Counter('payment_create_order_1_success_200');
export const order1Conflict409 = new Counter('payment_create_order_1_conflict_409');
export const order1ServerError500 = new Counter('payment_create_order_1_server_error_500');
export const order1UnexpectedStatus = new Counter('payment_create_order_1_unexpected_status');
export const order2Success200 = new Counter('payment_create_order_2_success_200');
export const order2Conflict409 = new Counter('payment_create_order_2_conflict_409');
export const order2ServerError500 = new Counter('payment_create_order_2_server_error_500');
export const order2UnexpectedStatus = new Counter('payment_create_order_2_unexpected_status');
export const order3Success200 = new Counter('payment_create_order_3_success_200');
export const order3Conflict409 = new Counter('payment_create_order_3_conflict_409');
export const order3ServerError500 = new Counter('payment_create_order_3_server_error_500');
export const order3UnexpectedStatus = new Counter('payment_create_order_3_unexpected_status');
export const order4Success200 = new Counter('payment_create_order_4_success_200');
export const order4Conflict409 = new Counter('payment_create_order_4_conflict_409');
export const order4ServerError500 = new Counter('payment_create_order_4_server_error_500');
export const order4UnexpectedStatus = new Counter('payment_create_order_4_unexpected_status');
export const order5Success200 = new Counter('payment_create_order_5_success_200');
export const order5Conflict409 = new Counter('payment_create_order_5_conflict_409');
export const order5ServerError500 = new Counter('payment_create_order_5_server_error_500');
export const order5UnexpectedStatus = new Counter('payment_create_order_5_unexpected_status');

const perOrderCounters = {
  1: {
    success200: order1Success200,
    conflict409: order1Conflict409,
    serverError500: order1ServerError500,
    unexpectedStatus: order1UnexpectedStatus,
  },
  2: {
    success200: order2Success200,
    conflict409: order2Conflict409,
    serverError500: order2ServerError500,
    unexpectedStatus: order2UnexpectedStatus,
  },
  3: {
    success200: order3Success200,
    conflict409: order3Conflict409,
    serverError500: order3ServerError500,
    unexpectedStatus: order3UnexpectedStatus,
  },
  4: {
    success200: order4Success200,
    conflict409: order4Conflict409,
    serverError500: order4ServerError500,
    unexpectedStatus: order4UnexpectedStatus,
  },
  5: {
    success200: order5Success200,
    conflict409: order5Conflict409,
    serverError500: order5ServerError500,
    unexpectedStatus: order5UnexpectedStatus,
  },
};

const scenarios = Object.fromEntries(
    ORDER_IDS.map((orderId, index) => [
      `order_${orderId}`,
      {
        executor: 'per-vu-iterations',
        exec: 'createPayment',
        vus: VUS,
        iterations: 1,
        maxDuration: '30s',
        startTime: `${index * ROUND_GAP_SECONDS}s`,
        env: {
          ORDER_ID: String(orderId),
        },
      },
    ])
);

const thresholds = {
  http_reqs: [`count==${TOTAL_EXPECTED_REQUESTS}`],
  payment_create_success_200: [`count==${ORDER_IDS.length * EXPECTED_SUCCESS}`],
  payment_create_conflict_409: [`count==${ORDER_IDS.length * EXPECTED_CONFLICT}`],
  payment_create_server_error_500: [`count==${ORDER_IDS.length * EXPECTED_SERVER_ERROR}`],
  payment_create_unexpected_status: ['count==0'],
};

for (const orderId of ORDER_IDS) {
  thresholds[`payment_create_order_${orderId}_success_200`] = [`count==${EXPECTED_SUCCESS}`];
  thresholds[`payment_create_order_${orderId}_conflict_409`] = [`count==${EXPECTED_CONFLICT}`];
  thresholds[`payment_create_order_${orderId}_server_error_500`] = [`count==${EXPECTED_SERVER_ERROR}`];
  thresholds[`payment_create_order_${orderId}_unexpected_status`] = ['count==0'];
}

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

export function createPayment() {
  const orderId = Number(__ENV.ORDER_ID);
  const payload = JSON.stringify({ orderId });
  const response = http.post(`${BASE_URL}/api/payments`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      endpoint: 'POST /api/payments',
      order_id: String(orderId),
      test_purpose: 'prevent-duplicate-payment-by-order',
    },
  });
  const orderCounters = perOrderCounters[orderId];

  if (response.status === 200) {
    success200.add(1);
    orderCounters?.success200.add(1);
  } else if (response.status === 409) {
    conflict409.add(1);
    orderCounters?.conflict409.add(1);
  } else if (response.status === 500) {
    serverError500.add(1);
    orderCounters?.serverError500.add(1);
  } else {
    unexpectedStatus.add(1);
    orderCounters?.unexpectedStatus.add(1);
  }

  check(response, {
    'status is one of 200, 409, 500': (res) => [200, 409, 500].includes(res.status),
  });
}

export function handleSummary(data) {
  const perOrder = Object.fromEntries(
      ORDER_IDS.map((orderId) => [
        String(orderId),
        {
          success200: data.metrics[`payment_create_order_${orderId}_success_200`]?.values.count ?? 0,
          conflict409: data.metrics[`payment_create_order_${orderId}_conflict_409`]?.values.count ?? 0,
          serverError500:
              data.metrics[`payment_create_order_${orderId}_server_error_500`]?.values.count ?? 0,
          unexpectedStatus:
              data.metrics[`payment_create_order_${orderId}_unexpected_status`]?.values.count ?? 0,
        },
      ])
  );

  const summary = {
    target: `${BASE_URL}/api/payments`,
    orderIds: ORDER_IDS,
    vus: VUS,
    expected: {
      success200: EXPECTED_SUCCESS,
      conflict409: EXPECTED_CONFLICT,
      serverError500: EXPECTED_SERVER_ERROR,
    },
    actual: {
      success200: data.metrics.payment_create_success_200?.values.count ?? 0,
      conflict409: data.metrics.payment_create_conflict_409?.values.count ?? 0,
      serverError500: data.metrics.payment_create_server_error_500?.values.count ?? 0,
      unexpectedStatus: data.metrics.payment_create_unexpected_status?.values.count ?? 0,
      totalRequests: data.metrics.http_reqs?.values.count ?? 0,
      avgDurationMs: data.metrics.http_req_duration?.values.avg ?? 0,
      p95DurationMs: data.metrics.http_req_duration?.values['p(95)'] ?? 0,
    },
    perOrder,
  };

  return {
    'k6/results/payment-create-duplicate-guard-summary.json': JSON.stringify(summary, null, 2),
    stdout: [
      '',
      '=== Payment Duplicate Guard Summary ===',
      `Target: ${summary.target}`,
      `orderIds: ${summary.orderIds.join(', ')}`,
      `Concurrent requests per round: ${summary.vus}`,
      `Total rounds: ${summary.orderIds.length}`,
      `200 success count: ${summary.actual.success200}`,
      `409 conflict count: ${summary.actual.conflict409}`,
      `500 error count: ${summary.actual.serverError500}`,
      `Unexpected status count: ${summary.actual.unexpectedStatus}`,
      `Total requests: ${summary.actual.totalRequests}`,
      `Average duration(ms): ${summary.actual.avgDurationMs.toFixed(2)}`,
      `p95 duration(ms): ${summary.actual.p95DurationMs.toFixed(2)}`,
      ...ORDER_IDS.flatMap((orderId) => {
        const orderSummary = summary.perOrder[String(orderId)];
        return [
          `orderId=${orderId} -> 200: ${orderSummary.success200}, 409: ${orderSummary.conflict409}, 500: ${orderSummary.serverError500}, unexpected: ${orderSummary.unexpectedStatus}`,
        ];
      }),
      '',
    ].join('\n'),
  };
}