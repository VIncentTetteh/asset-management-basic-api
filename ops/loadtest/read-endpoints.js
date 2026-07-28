// k6 load test for AssetIQ read-heavy endpoints.
//
// Covers the go-live capacity question (market-readiness plan #14): drive the
// dashboard + asset read paths at 50 / 200 / 500 concurrent virtual users and
// publish p95/p99 latency and error rate.
//
// Run:
//   k6 run -e BASE_URL=https://staging-api.assetiq.io \
//          -e EMAIL=loadtest@yourorg.com -e PASSWORD=... \
//          ops/loadtest/read-endpoints.js
//
// IMPORTANT: the API rate-limits authenticated traffic (default 100/min per
// principal — see RateLimitingInterceptor). A real capacity test must either
// raise that limit for the load-test user/IP, or fan out across many users.
// Against a single user with the default limit you will measure the limiter,
// not the server. Set LOADTEST_RATE_LIMIT high (or disable it) in the target
// environment before trusting the throughput numbers.

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.EMAIL || "ama.boateng@kwabenya.com.gh";
const PASSWORD = __ENV.PASSWORD || "Password123!";

const loginTrend = new Trend("login_duration", true);

export const options = {
    scenarios: {
        ramp: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "1m", target: 50 },
                { duration: "2m", target: 200 },
                { duration: "2m", target: 500 },
                { duration: "1m", target: 0 },
            ],
            gracefulRampDown: "30s",
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.01"], // <1% errors
        "http_req_duration{endpoint:assets}": ["p(95)<500"],
        "http_req_duration{endpoint:dashboard}": ["p(95)<500"],
        "http_req_duration{endpoint:stats}": ["p(95)<800"],
    },
};

// One login per VU init; token + org reused for that VU's iterations.
export function setup() {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: EMAIL, password: PASSWORD }),
        { headers: { "Content-Type": "application/json" } },
    );
    loginTrend.add(res.timings.duration);
    check(res, { "login 200": (r) => r.status === 200 });
    const token = res.json("token");
    // organisationId lives in the JWT payload.
    const payload = JSON.parse(
        decodeBase64Url(token.split(".")[1]),
    );
    return { token, orgId: payload.organisationId };
}

function decodeBase64Url(s) {
    const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
    return encoding_atob(s.replace(/-/g, "+").replace(/_/g, "/") + pad);
}

// k6 exposes atob via the encoding module in recent versions; fall back to a
// minimal decoder so the script runs on older k6 too.
import encoding from "k6/encoding";
function encoding_atob(b64) {
    return String.fromCharCode(...new Uint8Array(encoding.b64decode(b64)));
}

export default function (data) {
    const headers = {
        Authorization: `Bearer ${data.token}`,
        "X-Organisation-Id": data.orgId,
    };

    const endpoints = [
        { name: "assets", url: `${BASE_URL}/api/v1/assets?page=0&size=20` },
        { name: "dashboard", url: `${BASE_URL}/api/v1/dashboard/summary` },
        { name: "stats", url: `${BASE_URL}/api/v1/assets/stats` },
    ];

    for (const ep of endpoints) {
        const res = http.get(ep.url, { headers, tags: { endpoint: ep.name } });
        check(res, { [`${ep.name} 200`]: (r) => r.status === 200 });
    }
    sleep(1);
}
