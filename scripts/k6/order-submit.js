import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const token = __ENV.USER_TOKEN;
const idempotencyKey = __ENV.IDEMPOTENCY_KEY || `k6-order-${Date.now()}-${__VU}-${__ITER}`;

export default function () {
  if (!token) {
    throw new Error('USER_TOKEN is required. Prepare auth, cart data, and stock data before running this script.');
  }

  const payload = JSON.stringify({
    remark: __ENV.REMARK || 'k6 manual smoke order',
  });
  const res = http.post(`${baseUrl}/order/submit`, payload, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
      'X-Trace-Id': `k6-ordersubmit-${__VU}-${__ITER}`,
    },
  });
  check(res, {
    'order submit returns business response': (r) => [200, 400, 401, 409].includes(r.status),
    'order submit has trace header': (r) => Boolean(r.headers['X-Trace-Id']),
  });
  sleep(1);
}
