import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 50);
const EXPECTED_SUCCESS = Number(__ENV.EXPECTED_SUCCESS || 1);
const EXPECTED_CONFLICT = Number(__ENV.EXPECTED_CONFLICT || VUS - EXPECTED_SUCCESS);

export const paymentConfirmSuccess200 = new Counter('payment_confirm_success_200');
export const paymentConfirmConflict409 = new Counter('payment_confirm_conflict_409');
export const paymentConfirmUnexpected = new Counter('payment_confirm_unexpected');

export const options = {
  scenarios: {
    concurrent_confirm: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    payment_confirm_success_200: [`count==${EXPECTED_SUCCESS}`],
    payment_confirm_conflict_409: [`count==${EXPECTED_CONFLICT}`],
    payment_confirm_unexpected: ['count==0'],
    checks: ['rate==1.0'],
  },
};

export function setup() {
  const response = http.post(`${BASE_URL}/test/payment-confirm-concurrency/fixtures`);
  check(response, {
    'fixture created': (res) => res.status === 200,
  });

  if (response.status !== 200) {
    throw new Error(`fixture 생성 실패 status=${response.status}, body=${response.body}`);
  }

  return response.json();
}

export default function (fixture) {
  const payload = JSON.stringify({
    paymentKey: fixture.paymentKey,
    paymentId: fixture.paymentId,
    orderId: fixture.orderId,
    amount: fixture.amount,
  });

  const response = http.post(`${BASE_URL}/api/payments/confirm`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (response.status === 200) {
    paymentConfirmSuccess200.add(1);
    return;
  }

  if (response.status === 409) {
    paymentConfirmConflict409.add(1);
    return;
  }

  paymentConfirmUnexpected.add(1);
}

export function teardown(fixture) {
  const statsResponse = http.get(`${BASE_URL}/test/payment-confirm-concurrency/toss-stats`);
  const resultResponse = http.get(`${BASE_URL}/test/payment-confirm-concurrency/fixtures/${fixture.paymentId}/result`);

  check(statsResponse, {
    'mock toss confirm count is 1': (res) => res.status === 200 && res.json('confirmCount') === 1,
  });
  check(resultResponse, {
    'payment completed once': (res) => res.status === 200 && res.json('paymentStatus') === 'SUCCESS',
    'order completed once': (res) => res.status === 200 && res.json('orderStatus') === 'COMPLETED',
    'toss count in result is 1': (res) => res.status === 200 && res.json('tossConfirmCount') === 1,
  });
}

export function handleSummary(data) {
  return {
    'stdout': JSON.stringify(data.metrics, null, 2),
    'k6/results/payment-confirm-concurrency-cas-summary.json': JSON.stringify(data, null, 2),
  };
}
