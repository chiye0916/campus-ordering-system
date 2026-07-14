import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';

export default function () {
  const tradeNo = required('TRADE_NO');
  const amount = required('AMOUNT');
  const callbackNo = __ENV.CALLBACK_NO || `k6-callback-${Date.now()}-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    tradeNo,
    callbackNo,
    thirdTradeNo: __ENV.THIRD_TRADE_NO || callbackNo,
    payStatus: __ENV.PAY_STATUS || 'SUCCESS',
    amount,
    callbackTime: __ENV.CALLBACK_TIME || new Date().toISOString().replace('Z', ''),
  });

  const res = http.post(`${baseUrl}/payment/mock/callback`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Trace-Id': `k6-paycallback-${__VU}-${__ITER}`,
    },
  });
  check(res, {
    'payment callback returns business response': (r) => [200, 400, 409].includes(r.status),
    'payment callback has trace header': (r) => Boolean(r.headers['X-Trace-Id']),
  });
  sleep(1);
}

function required(name) {
  if (!__ENV[name]) {
    throw new Error(`${name} is required. Use valid mock payment trade data from a prepared order.`);
  }
  return __ENV[name];
}
