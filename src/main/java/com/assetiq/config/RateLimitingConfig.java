package com.assetiq.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting configuration backed by two separate bounded, expiring bucket caches:
 *
 *  - AUTH bucket  : strict — 5 requests / minute, 20 requests / hour.
 *                   Applied to /api/v1/auth/** and /api/v1/mfa/** endpoints to
 *                   prevent brute-force credential attacks.
 *
 *  - GENERAL bucket: relaxed — 100 requests / minute.
 *                   Applied to all other /api/** paths.
 *
 * Bucket keys are namespaced ("auth:<clientKey>" vs "api:<clientKey>") so each
 * client gets independent counters for the two tiers.
 *
 * Memory safety: each cache is bounded at MAX_BUCKETS entries with LRU eviction
 * AND a 1-hour TTL on creation time.  For very high-traffic production deployments
 * swap the LinkedHashMap for a Caffeine cache (spring-boot-starter-cache already
 * brings caffeine on the classpath via spring-cache auto-configuration).
 */
@Configuration
public class RateLimitingConfig {

    // ── Limits ────────────────────────────────────────────────────────────────

    /** Requests allowed per minute for auth endpoints (login, MFA, token refresh). */
    public static final int AUTH_REQUESTS_PER_MINUTE  = 5;
    /** Hourly hard-cap for auth endpoints, providing a second-layer brake. */
    public static final int AUTH_REQUESTS_PER_HOUR    = 20;

    /** Requests allowed per minute for all other authenticated API calls. */
    public static final int API_REQUESTS_PER_MINUTE   = 100;

    // ── Response headers ─────────────────────────────────────────────────────

    public static final String HEADER_REMAINING    = "X-RateLimit-Remaining";
    public static final String HEADER_LIMIT        = "X-RateLimit-Limit";
    public static final String HEADER_RESET        = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER  = "Retry-After";

    // ── Bucket caches ─────────────────────────────────────────────────────────

    private static final int MAX_BUCKETS = 50_000;

    private static final Map<String, BucketEntry> AUTH_CACHE    = buildCache();
    private static final Map<String, BucketEntry> GENERAL_CACHE = buildCache();

    @SuppressWarnings("serial")
    private static Map<String, BucketEntry> buildCache() {
        return Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BucketEntry> eldest) {
                    return size() > MAX_BUCKETS
                        || (System.currentTimeMillis() - eldest.getValue().createdAt()) > TimeUnit.HOURS.toMillis(1);
                }
            }
        );
    }

    private record BucketEntry(Bucket bucket, long createdAt) {}

    // ── Public resolution API ─────────────────────────────────────────────────

    /**
     * Returns the auth-tier bucket for {@code clientKey}.
     * The bucket enforces both a per-minute and per-hour limit.
     */
    public static Bucket resolveAuthBucket(String clientKey) {
        return AUTH_CACHE.computeIfAbsent("auth:" + clientKey,
            k -> new BucketEntry(createAuthBucket(), System.currentTimeMillis())).bucket();
    }

    /**
     * Returns the general-API bucket for {@code clientKey}.
     */
    public static Bucket resolveGeneralBucket(String clientKey) {
        return GENERAL_CACHE.computeIfAbsent("api:" + clientKey,
            k -> new BucketEntry(createGeneralBucket(), System.currentTimeMillis())).bucket();
    }

    // ── Bucket factories ──────────────────────────────────────────────────────

    private static Bucket createAuthBucket() {
        // Two-tier: per-minute burst + per-hour hard cap (greedy refill for the minute,
        // intervally for the hour so it resets cleanly at the boundary).
        Bandwidth perMinute = Bandwidth.classic(
            AUTH_REQUESTS_PER_MINUTE,
            Refill.greedy(AUTH_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));

        Bandwidth perHour = Bandwidth.classic(
            AUTH_REQUESTS_PER_HOUR,
            Refill.intervally(AUTH_REQUESTS_PER_HOUR, Duration.ofHours(1)));

        return Bucket.builder()
            .addLimit(perMinute)
            .addLimit(perHour)
            .build();
    }

    private static Bucket createGeneralBucket() {
        Bandwidth perMinute = Bandwidth.classic(
            API_REQUESTS_PER_MINUTE,
            Refill.greedy(API_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));

        return Bucket.builder()
            .addLimit(perMinute)
            .build();
    }
}
