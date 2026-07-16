package com.assetiq.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static com.assetiq.config.RateLimitingConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitingInterceptor}.
 *
 * Redis interactions are mocked via {@link RedisRateLimiter} so these tests
 * run without a live Redis instance.  The tests exercise:
 *
 *  1. Auth endpoints use the strict AUTH tier (5 req/min + 20 req/hour).
 *  2. Non-auth endpoints use the relaxed API tier (100 req/min).
 *  3. 429 is returned when either auth sub-limit is exhausted.
 *  4. Correct RFC 6585 headers are written on allowed and rejected requests.
 *  5. Client key extraction precedence (X-Client-ID → JWT hash → IP).
 *  6. X-Forwarded-For is only trusted when remoteAddr is in a trusted CIDR.
 *  7. Rate limiting can be disabled via config (test / dev shortcut).
 */
@ExtendWith(MockitoExtension.class)
class RateLimitingInterceptorTest {

    @Mock
    private RedisRateLimiter rateLimiter;

    private RateLimitingConfig config;
    private RateLimitingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        config = new RateLimitingConfig();
        config.setEnabled(true);
        config.setTrustedProxyCidrs(List.of());
        interceptor = new RateLimitingInterceptor(rateLimiter, config);
    }

    // ── Auth tier ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Auth endpoint rate limiting")
    class AuthEndpoints {

        @Test
        @DisplayName("Allows request when both minute and hour limits have tokens")
        void authRequest_allowedWhenBothLimitsOk() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_AUTH_MINUTE), any(), eq(AUTH_REQUESTS_PER_MINUTE), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 4, 55));
            when(rateLimiter.tryConsume(eq(TIER_AUTH_HOUR), any(), eq(AUTH_REQUESTS_PER_HOUR), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 19, 3550));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean allowed = interceptor.preHandle(authRequest("10.0.0.1"), resp, new Object());

            assertThat(allowed).isTrue();
            assertThat(resp.getStatus()).isNotEqualTo(429);
            assertThat(resp.getHeader(HEADER_LIMIT)).isEqualTo(String.valueOf(AUTH_REQUESTS_PER_MINUTE));
        }

        @Test
        @DisplayName("Returns 429 when per-minute auth limit is exhausted")
        void authRequest_blockedOnPerMinuteExhaustion() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_AUTH_MINUTE), any(), eq(AUTH_REQUESTS_PER_MINUTE), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(false, 0, 42));
            when(rateLimiter.tryConsume(eq(TIER_AUTH_HOUR), any(), eq(AUTH_REQUESTS_PER_HOUR), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 15, 3000));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean allowed = interceptor.preHandle(authRequest("10.0.0.2"), resp, new Object());

            assertThat(allowed).isFalse();
            assertThat(resp.getStatus()).isEqualTo(429);
            assertThat(resp.getHeader(HEADER_RETRY_AFTER)).isNotNull();
        }

        @Test
        @DisplayName("Returns 429 when per-hour auth limit is exhausted even if minute is ok")
        void authRequest_blockedOnPerHourExhaustion() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_AUTH_MINUTE), any(), eq(AUTH_REQUESTS_PER_MINUTE), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 3, 55));
            when(rateLimiter.tryConsume(eq(TIER_AUTH_HOUR), any(), eq(AUTH_REQUESTS_PER_HOUR), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(false, 0, 1800));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean allowed = interceptor.preHandle(authRequest("10.0.0.3"), resp, new Object());

            assertThat(allowed).isFalse();
            assertThat(resp.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("MFA paths also use the auth tier")
        void mfaPath_usesAuthTier() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_AUTH_MINUTE), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 4, 55));
            when(rateLimiter.tryConsume(eq(TIER_AUTH_HOUR), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 19, 3000));

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/mfa/verify");
            req.setRemoteAddr("10.0.0.4");

            boolean allowed = interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
            assertThat(allowed).isTrue();

            // Verify auth tiers (not API tier) were consulted
            verify(rateLimiter).tryConsume(eq(TIER_AUTH_MINUTE), any(), anyInt(), anyInt());
            verify(rateLimiter).tryConsume(eq(TIER_AUTH_HOUR), any(), anyInt(), anyInt());
            verify(rateLimiter, never()).tryConsume(eq(TIER_API_MINUTE), any(), anyInt(), anyInt());
        }
    }

    // ── General API tier ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("General API rate limiting")
    class GeneralEndpoints {

        @Test
        @DisplayName("Sets X-RateLimit-Limit to API_REQUESTS_PER_MINUTE for non-auth paths")
        void generalRequest_setsCorrectLimitHeader() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_API_MINUTE), any(), eq(API_REQUESTS_PER_MINUTE), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            interceptor.preHandle(generalRequest("192.168.2.1"), resp, new Object());

            assertThat(resp.getHeader(HEADER_LIMIT)).isEqualTo(String.valueOf(API_REQUESTS_PER_MINUTE));
        }

        @Test
        @DisplayName("X-RateLimit-Remaining reflects value from Redis rate limiter")
        void generalRequest_remainingFromRedisBucket() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_API_MINUTE), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 73, 55));

            MockHttpServletResponse resp = new MockHttpServletResponse();
            interceptor.preHandle(generalRequest("192.168.2.2"), resp, new Object());

            assertThat(resp.getHeader(HEADER_REMAINING)).isEqualTo("73");
        }

        @Test
        @DisplayName("General tier does NOT consult auth sub-limits")
        void generalRequest_doesNotUseAuthTiers() throws Exception {
            when(rateLimiter.tryConsume(eq(TIER_API_MINUTE), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            interceptor.preHandle(generalRequest("192.168.2.3"), new MockHttpServletResponse(), new Object());

            verify(rateLimiter, never()).tryConsume(eq(TIER_AUTH_MINUTE), any(), anyInt(), anyInt());
            verify(rateLimiter, never()).tryConsume(eq(TIER_AUTH_HOUR),   any(), anyInt(), anyInt());
        }
    }

    // ── Client key extraction ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Client key extraction")
    class ClientKeyExtraction {

        @Test
        @DisplayName("X-Client-ID header takes highest precedence")
        void clientId_xClientIdTakesPriority() throws Exception {
            when(rateLimiter.tryConsume(any(), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            MockHttpServletRequest req = generalRequest("10.4.0.1");
            req.addHeader("X-Client-ID", "my-app-v2");
            req.addHeader("Authorization", "Bearer sometoken");
            req.addHeader("X-Forwarded-For", "203.0.113.1");

            interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            // Bucket key should start with "cid:"
            verify(rateLimiter).tryConsume(eq(TIER_API_MINUTE), argThat(k -> k.startsWith("cid:")), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Bearer token hash used when no X-Client-ID present")
        void clientId_bearerTokenSecondPriority() throws Exception {
            when(rateLimiter.tryConsume(any(), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            MockHttpServletRequest req = generalRequest("10.4.0.2");
            req.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.abc");

            interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            verify(rateLimiter).tryConsume(eq(TIER_API_MINUTE), argThat(k -> k.startsWith("jwt:")), anyInt(), anyInt());
        }

        @Test
        @DisplayName("X-Forwarded-For is NOT trusted when remoteAddr is not a trusted proxy")
        void clientId_forwardedForIgnoredForUntrustedRemote() throws Exception {
            when(rateLimiter.tryConsume(any(), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            // No trusted proxies configured — default empty list
            MockHttpServletRequest req = generalRequest("203.0.113.42");
            req.addHeader("X-Forwarded-For", "1.2.3.4");  // attacker-controlled header

            interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            // Key should use remoteAddr (203.0.113.42), NOT the spoofed X-Forwarded-For (1.2.3.4)
            verify(rateLimiter).tryConsume(
                    eq(TIER_API_MINUTE),
                    argThat(k -> k.contains("203.0.113.42") && !k.contains("1.2.3.4")),
                    anyInt(), anyInt());
        }

        @Test
        @DisplayName("X-Forwarded-For IS trusted when remoteAddr is in a trusted CIDR")
        void clientId_forwardedForTrustedForKnownProxy() throws Exception {
            config.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
            when(rateLimiter.tryConsume(any(), any(), anyInt(), anyInt()))
                    .thenReturn(new RedisRateLimiter.RateLimitResult(true, 99, 55));

            MockHttpServletRequest req = generalRequest("10.0.0.1");  // trusted proxy
            req.addHeader("X-Forwarded-For", "203.0.113.77");

            interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

            // Should use the forwarded client IP
            verify(rateLimiter).tryConsume(
                    eq(TIER_API_MINUTE),
                    argThat(k -> k.contains("203.0.113.77")),
                    anyInt(), anyInt());
        }
    }

    // ── Disabled state ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate limiting disabled")
    class Disabled {

        @Test
        @DisplayName("All requests are allowed when rate limiting is disabled")
        void disabled_allRequestsPass() throws Exception {
            config.setEnabled(false);

            MockHttpServletResponse resp = new MockHttpServletResponse();
            boolean allowed = interceptor.preHandle(authRequest("10.0.0.1"), resp, new Object());

            assertThat(allowed).isTrue();
            assertThat(resp.getStatus()).isNotEqualTo(429);
            // Redis should never be consulted when disabled
            verifyNoInteractions(rateLimiter);
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
