package com.assetiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Rate limiting constants and configuration.
 *
 * Bucket state is no longer held here — it lives in Redis, managed by
 * {@link RedisRateLimiter}. This class only carries the limit parameters
 * and the trusted-proxy list used by {@link RateLimitingInterceptor}.
 *
 * <h3>Trusted proxies</h3>
 * {@code app.rate-limiting.trusted-proxy-cidrs} — list of CIDR ranges whose
 * traffic may have the {@code X-Forwarded-For} header trusted. In production
 * set this to your load balancer's egress IP range (e.g. the ALB/Nginx CIDR).
 * Requests from untrusted remoteAddr fall back to using remoteAddr directly,
 * making header spoofing impossible.
 */
@Configuration
@ConfigurationProperties(prefix = "app.rate-limiting")
public class RateLimitingConfig {

    // ── Limits ────────────────────────────────────────────────────────────────

    /** Requests allowed per minute on auth endpoints (login, MFA, token refresh). */
    public static final int AUTH_REQUESTS_PER_MINUTE  = 5;
    /** Per-hour hard-cap on auth endpoints — secondary brake against slow brute force. */
    public static final int AUTH_REQUESTS_PER_HOUR    = 20;
    /** Requests allowed per minute for general API calls. */
    public static final int API_REQUESTS_PER_MINUTE   = 100;

    // ── Rate-limit tier names (used as Redis key segments) ────────────────────

    public static final String TIER_AUTH_MINUTE  = "auth:minute";
    public static final String TIER_AUTH_HOUR    = "auth:hour";
    public static final String TIER_API_MINUTE   = "api:minute";

    // ── Response headers (RFC 6585 / draft-ietf-httpapi-ratelimit-headers) ────

    public static final String HEADER_REMAINING    = "X-RateLimit-Remaining";
    public static final String HEADER_LIMIT        = "X-RateLimit-Limit";
    public static final String HEADER_RESET        = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER  = "Retry-After";

    // ── Bound properties ──────────────────────────────────────────────────────

    /** Whether rate limiting is active (can be disabled in test profiles). */
    private boolean enabled = true;

    /**
     * CIDR ranges of trusted reverse proxies.
     *
     * When a request's {@code remoteAddr} falls within one of these ranges the
     * interceptor trusts the leftmost IP in {@code X-Forwarded-For} as the real
     * client address. All other requests use {@code remoteAddr} directly.
     *
     * Example (docker-compose / nginx on the same host):
     *   app.rate-limiting.trusted-proxy-cidrs: ["127.0.0.1/32","172.17.0.0/16"]
     *
     * Example (AWS ALB):
     *   app.rate-limiting.trusted-proxy-cidrs: ["10.0.0.0/8"]
     *
     * Default (empty list): never trust X-Forwarded-For — always use remoteAddr.
     */
    private List<String> trustedProxyCidrs = List.of();

    // ── Getters / setters (needed by @ConfigurationProperties binding) ────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getTrustedProxyCidrs() { return trustedProxyCidrs; }
    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs != null ? trustedProxyCidrs : List.of();
    }
}
