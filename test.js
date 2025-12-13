import http from 'k6/http';

/**
 * WAYGRID – HTTP ORIGIN CONSTANT ARRIVAL RATE TEST
 *
 * Purpose:
 *  - push a fixed number of requests per second
 *  - find the real throughput ceiling
 *  - verify stability under sustained pressure
 *
 * This test intentionally:
 *  - removes checks (performance > correctness)
 *  - minimizes JS overhead
 */

export const options = {
  scenarios: {
    constant_rate: {
      executor: 'constant-arrival-rate',

      // 🔥 TARGET LOAD (tune this)
      rate: 30_000,          // requests per second
      timeUnit: '1s',
      duration: '10m',

      // VU pool (k6 will use as many as needed)
      preAllocatedVUs: 2000,
      maxVUs: 5000,
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.01'],   // <1% errors
    http_req_duration: [
      'p(95)<100',                    // relaxed on purpose
      'p(99)<250',
    ],
  },
};

/* =========================
   REQUEST DEFINITION
   ========================= */

const URL = 'http://localhost:1337/v1/ingest';

const PAYLOAD = JSON.stringify({
  graph: {
    entryPoint: {
      address: 'waygrid://test/service',
      retryPolicy: { type: 'None' },
      deliveryStrategy: {
        type: 'ScheduleAfter',
        delay: '1 minute',
      },
      onSuccess: null,
      onFailure: null,
      label: null,
    },
    repeatPolicy: { type: 'NoRepeat' },
  },
});

const PARAMS = {
  headers: {
    'Content-Type': 'application/json',
  },
};

/* =========================
   TEST EXECUTION
   ========================= */

export default function () {
  http.post(URL, PAYLOAD, PARAMS);
}
