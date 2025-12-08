import http from 'k6/http';
import { check, sleep } from 'k6';

// === CONFIGURATION ===
// Burst-style test: short, high-intensity spikes
export const options = {
  scenarios: {
    bursts: {
      executor: 'per-vu-iterations',
      vus: 5000,                  // number of concurrent virtual users
      iterations: 100,             // how many requests each VU sends
      maxDuration: '30s',        // stop after 30s
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],      // <1% errors
    http_req_duration: ['p(95)<100'],   // 95% under 1s
  },
};

// === TEST BODY ===
export default function () {
  const url = 'http://localhost:1337/v1/ingest';

  const payload = JSON.stringify({
    graph: {
      entryPoint: {
        address: 'waygrid://test/service',
        retryPolicy: {type: 'None'},
        deliveryStrategy: {
          delay: '1 minute',
          type: 'ScheduleAfter',
        },
        onSuccess: null,
        onFailure: null,
        label: null,
      },
      repeatPolicy: {type: 'NoRepeat'},
    },
  });

  const params = {
    headers: {'Content-Type': 'application/json'},
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
