import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics
const paymentSuccess = new Counter('payment_success');
const paymentFailure = new Counter('payment_failure');
const errorRate = new Rate('error_rate');

// Test configuration
export const options = {
  scenarios: {
    default: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    error_rate: ['rate<0.5'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export default function () {
  const endpoints = [
    { method: 'GET', path: '/' },
    { method: 'GET', path: '/io_task' },
    { method: 'GET', path: '/cpu_task' },
    { method: 'GET', path: '/random_sleep' },
    { method: 'GET', path: '/random_status' },
    { method: 'GET', path: '/chain' },
    { method: 'POST', path: `/payment?amount=${Math.floor(Math.random() * 1000)}` },
  ];

  endpoints.forEach(({ method, path }) => {
    const url = `${BASE_URL}${path}`;
    const res = method === 'POST' ? http.post(url) : http.get(url);

    const success = res.status >= 200 && res.status < 400;
    errorRate.add(!success);

    if (path.includes('/payment')) {
      if (success) {
        paymentSuccess.add(1);
      } else {
        paymentFailure.add(1);
      }
    }
  });

  sleep(0.5);
}

// Payment-focused scenario
export function paymentLoad() {
  const amount = Math.floor(Math.random() * 1000);
  const res = http.post(`${BASE_URL}/payment?amount=${amount}`);

  check(res, {
    'payment processed': (r) => r.status === 200 || r.status === 400 || r.status === 402,
  });

  errorRate.add(res.status >= 500);
  sleep(0.1);
}
