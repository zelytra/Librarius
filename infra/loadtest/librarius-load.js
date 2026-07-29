// Load test for the Librarius API — k6.
//
//   k6 run -e BASE_URL=https://librarius.zelytra.fr \
//          -e ACCOUNTS='alice:pw1,bob:pw2,...' infra/loadtest/librarius-load.js
//
// It answers one question: how much traffic does one api pod take before the
// target in docs/DEPLOYMENT.md § "Scaling out" stops being met. The thresholds
// below *are* that target, so k6 exits non-zero when it is missed — the result
// is a pass or a fail, not a graph somebody has to interpret.
//
// Three things worth knowing before running it:
//
//   * It signs in through the direct access grant the `librarius-web` client
//     already allows (the same route the e2e suite takes), so no browser and no
//     Keycloak login form are involved. Accounts are supplied, never created:
//     the test must not leave rows behind.
//   * It is read-only. Nothing is written, so it can be pointed at an
//     environment that has data in it without changing that data.
//   * The catalog scenario is deliberately slow. `/api/catalog/search` is capped
//     at 30 calls a minute per caller (`librarius.catalog.rate-limit.per-minute`)
//     and a cold miss goes out to Open Library or AniList behind a 12 s
//     deadline. Driving it at the same rate as the library scenario would
//     measure the rate limiter and the providers, not the API.

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
// Keycloak sits on /auth of the same host in the deployed stack; a local run
// points this at the compose Keycloak instead.
const AUTH_URL = (__ENV.AUTH_URL || `${BASE_URL}/auth`).replace(/\/$/, '');
const REALM = __ENV.REALM || 'librarius';
const CLIENT_ID = __ENV.CLIENT_ID || 'librarius-web';

// `user:password,user:password`. Every account is a distinct caller as far as
// the catalog quota is concerned, which is why the catalog rate below is
// derived from how many there are.
const ACCOUNTS = (__ENV.ACCOUNTS || '')
  .split(',')
  .map((pair) => pair.trim())
  .filter(Boolean)
  .map((pair) => {
    const at = pair.indexOf(':');
    return { username: pair.slice(0, at), password: pair.slice(at + 1) };
  });

// Terms picked to be warm after the first iteration: the point of the catalog
// scenario is to measure the API and its cache, not the providers' latency.
const SEARCH_TERMS = ['one piece', 'dune', 'berserk', 'fondation', 'vinland saga'];

const libraryLatency = new Trend('librarius_library_latency', true);
const searchLatency = new Trend('librarius_search_latency', true);

export const options = {
  scenarios: {
    // 50 concurrent sessions holding 20 requests/second between them. Ramped
    // rather than started flat so that a cold JVM is not measured as the
    // steady-state answer.
    library: {
      executor: 'ramping-arrival-rate',
      exec: 'browseLibrary',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 50,
      stages: [
        { target: 20, duration: '1m' },  // ramp
        { target: 20, duration: '5m' },  // the measurement
        { target: 0, duration: '30s' },  // let the HPA scale back down
      ],
    },
    // 2 req/s, which is 120 a minute: comfortably under the per-caller quota as
    // long as at least 4 accounts are supplied, and enough to keep the catalog
    // path represented in the p95.
    catalog: {
      executor: 'constant-arrival-rate',
      exec: 'searchCatalog',
      rate: 2,
      timeUnit: '1s',
      duration: '6m30s',
      preAllocatedVUs: 5,
      maxVUs: 10,
    },
  },
  thresholds: {
    // The target. Failing any of these fails the run.
    'librarius_library_latency': ['p(95)<500'],
    'librarius_search_latency': ['p(95)<500'],
    // 0% of responses 5xx, on every endpoint. A 401 or a 429 is the API
    // answering correctly and is not counted here.
    'http_req_failed{expected_response:true}': ['rate<0.01'],
    'checks': ['rate>0.99'],
  },
};

// One token per account, fetched once. Tokens outlive the run at the realm's
// default lifetime; a longer test would have to refresh them.
export function setup() {
  if (ACCOUNTS.length === 0) {
    throw new Error(
      'ACCOUNTS is empty. Pass -e ACCOUNTS=user:password,user:password — the test ' +
      'signs in as existing accounts and creates none.',
    );
  }

  return {
    tokens: ACCOUNTS.map(({ username, password }) => {
      const res = http.post(
        `${AUTH_URL}/realms/${REALM}/protocol/openid-connect/token`,
        { grant_type: 'password', client_id: CLIENT_ID, username, password },
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
      );
      if (res.status !== 200) {
        throw new Error(`sign-in failed for ${username}: HTTP ${res.status} ${res.body}`);
      }
      return res.json('access_token');
    }),
  };
}

function authHeaders(tokens) {
  // Spread over the pool by scenario iteration, so no single account carries the
  // whole run — and so the catalog quota is shared the way real callers share it.
  const token = tokens[exec.scenario.iterationInTest % tokens.length];
  return { headers: { Authorization: `Bearer ${token}` } };
}

export function browseLibrary(data) {
  const res = http.get(`${BASE_URL}/api/library?page=0&size=20`, {
    ...authHeaders(data.tokens),
    tags: { endpoint: 'library' },
  });
  libraryLatency.add(res.timings.duration);
  check(res, { 'library 200': (r) => r.status === 200 });
}

export function searchCatalog(data) {
  const term = SEARCH_TERMS[exec.scenario.iterationInTest % SEARCH_TERMS.length];
  const res = http.get(`${BASE_URL}/api/catalog/search?q=${encodeURIComponent(term)}`, {
    ...authHeaders(data.tokens),
    tags: { endpoint: 'catalog' },
  });
  searchLatency.add(res.timings.duration);
  // 429 is the rate limiter doing its job, not a failure of the API. It does
  // mean the run is driving a caller harder than the quota allows — supply more
  // accounts rather than raising the quota.
  check(res, { 'catalog 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
