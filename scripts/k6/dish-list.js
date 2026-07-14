import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 10),
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const categoryId = __ENV.CATEGORY_ID || '1';

export default function () {
  const res = http.get(`${baseUrl}/dish/list?categoryId=${categoryId}`, {
    headers: { 'X-Trace-Id': `k6-dishlist-${__VU}-${__ITER}` },
  });
  check(res, {
    'dish list status is 200': (r) => r.status === 200,
    'dish list has trace header': (r) => Boolean(r.headers['X-Trace-Id']),
  });
  sleep(1);
}
