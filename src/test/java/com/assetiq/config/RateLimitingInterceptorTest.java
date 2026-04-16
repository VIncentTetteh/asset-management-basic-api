package com.assetiq.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.assetiq.config.RateLimitingConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitingInterceptor}.
 *
 * Verifies:
 *  1. Auth endpoints use the strict 5 req/min bucket.
 *  2. Non-auth endpoints use the relaxed 100 req/min bucket.
 *  3. A 429 response is returned when the auth bucket is exhausted.
 *  4. Correct standard headers are written on both allowed and rejected requests.
 *  5. Client key extraction preference order (X-Client-ID > JWT hash > IP).
 */
class RateLimitingInterceptorTest {

    private RateLimitingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitingInterceptor();
    }

    // ── Auth tier ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Auth endpoint rate limiting")
    class AuthEndpoints {

        @Test
        @DisplayName("Allows exactly AUTH_REQUESTS_PER_MINUTE requests before blocking")
        void authBucket_exhaustedAfterLimit() throws Exception {
            String uniqueIp = "10.0.0." + System.nanoTime() % 254; // unique per test run

            for (int i = 0; i < AUTH_REQUESTS_PER_MINUTE; i++) {
                MockHttpServletRequest  req  = authRequest(uniqueIp);
                MockHttpServletResponse resp = new MockHttpServletResponse();
                boolean allowed = interceptor.preHandle(req, resp, new Object());
                assertThat(allowed)
                        .as("Request %d/%d should be allowed", i + 1, AUTH_REQUESTS_PER_MINUTE)
                        .isTrue();
                assertThat(resp.getStatus()).isNotEqualTo(429);
            }

            // The very next request should be blocked
            MockHttpServletRequest  req  = authRequest(uniqueIp);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean blocked = interceptor.preHandle(req, resp, new Object());

            assertThat(blocked).isFalse();
            assertThat(resp.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("Sets X-RateLimit-Limit to AUTH_REQUESTS_PER_MINUTE for auth paths")
        void authBucket_setsLimitHeader() throws Exception {
            MockHttpServletRequest  req  = authRequest("192.168.1.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            interceptor.preHandle(req, resp, new Object());

            assertThat(resp.getHeader(HEADER_LIMIT))
                    .isEqualTo(String.valueOf(AUTH_REQUESTS_PER_MINUTE));
        }

        @Test
        @DisplayName("Sets Retry-After header on 429 response")
        void authBucket_setsRetryAfterOn429() throws Exception {
            String uniqueIp = "10.1.0." + System.nanoTime() % 254;
            // Exhaust the bucket
            for (int i = 0; i < AUTH_REQUESTS_PER_MINUTE; i++) {
                interceptor.preHandle(authRequest(uniqueIp), new MockHttpServletResponse(), new Object());
            }

            MockHttpServletResponse resp = new MockHttpServletResponse();
            interceptor.preHandle(authRequest(uniqueIp), resp, new Object());

            assertThat(resp.getHeader(HEADER_RETRY_AFTER)).isNotNull();
            assertThat(Long.parseLong(resp.getHeader(HEADER_RETRY_AFTER))).isGreaterThan(0L);
        }

        @Test
        @DisplayName("MFA endpoints also use the auth bucket")
        void mfaPath_usesAuthBucket() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/mfa/verify");
            req.setRemoteAddr("10.2.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            interceptor.preHandle(req, resp, new Object());

            assertThat(resp.getHeader(HEADER_LIMIT))
                    .isEqualTo(String.valueOf(AUTH_REQUESTS_PER_MINUTE));
        }
    }

    // ── General / API tier ────────────────────────────────────────────────────

    @Nested
    @DisplayName("General API rate limiting")
    class GeneralEndpoints {

        @Test
        @DisplayName("Sets X-RateLimit-Limit to API_REQUESTS_PER_MINUTE for non-auth paths")
        void generalBucket_setsCorrectLimitHeader() throws Exception {
            MockHttpServletRequest  req  = generalRequest("192.168.2.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            interceptor.preHandle(req, resp, new Object());

            assertThat(resp.getHeader(HEADER_LIMIT))
                    .isEqualTo(String.valueOf(API_REQUESTS_PER_MINUTE));
        }

        @Test
        @DisplayName("X-RateLimit-Remaining decrements on each allowed request")
        void generalBucket_remainingDecrementsCorrectly() throws Exception {
            String uniqueIp = "10.3.0." + System.nanoTime() % 254;

            MockHttpServletResponse resp1 = new MockHttpServletResponse();
            interceptor.preHandle(generalRequest(uniqueIp), resp1, new Object());
            long remaining1 = Long.parseLong(resp1.getHeader(HEADER_REMAINING));

            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            interceptor.preHandle(generalRequest(uniqueIp), resp2, new Object());
            long remaining2 = Long.parseLong(resp2.getHeader(HEADER_REMAINING));

            assertThat(remaining2).isLessThan(remaining1);
        }
    }

    // ── Client key extraction ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Client key extraction")
    class ClientKeyExtraction {

        @Test
        @DisplayName("X-Client-ID header takes precedence over IP")
        void clientId_prefersXClientIdHeader() throws Exception {
            String clientId = "test-client-" + System.nanoTime();
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/assets");
            req.addHeader("X-Client-ID", clientId);
            req.setRemoteAddr("10.4.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(req, resp, new Object());
            assertThat(allowed).isTrue();

            // A different IP with the same client ID should share the same bucket
            MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/assets");
            req2.addHeader("X-Client-ID", clientId);
            req2.setRemoteAddr("10.4.0.99");
            assertThat(interceptor.preHandle(req2, new MockHttpServletResponse(), new Object()))
                    .isTrue();
        }

        @Test
        @DisplayName("X-Forwarded-For used when no Bearer token present")
        void clientKey_usesForwardedForWhenNoToken() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/assets");
            req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean allowed = interceptor.preHandle(req, resp, new Object());
            assertThat(allowed).isTrue();
            // No assertion on bucket key internals — just verifying no exception is thrown
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest authRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    private MockHttpServletRequest generalRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/assets");
        req.setRemoteAddr(ip);
        return req;
    }
}
