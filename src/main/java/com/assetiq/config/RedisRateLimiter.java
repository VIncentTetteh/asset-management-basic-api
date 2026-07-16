package com.assetiq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Distributed rate limiter backed by Redis.
 *
 * Uses an atomic Lua script (INCR + EXPIRE) to implement a fixed-window
 * counter per client key. Because the script is executed atomically on the
 * Redis server, there is no TOCTOU race between reading the counter and
 * incrementing it — safe under high concurrency.
 *
 * Fail-open design: if Redis is unavailable the request is allowed through
 * with a WARN log. This prevents a Redis outage from taking down the API,
 * while still logging the degradation so ops can react.
 *
 * <h3>Key scheme</h3>
 * {@code ratelimit:{tier}:{clientKey}} — one key per client per tier.
 * TTL = window size in seconds (60 for per-minute, 3600 for per-hour).
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@Primary
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String KEY_PREFIX = "ratelimit:";

    /**
     * Atomic Lua script:
     *  1. Increment the counter.
     *  2. If this is the first increment (counter == 1) set the TTL so the
     *     window expires automatically — no separate cleanup job needed.
     *  3. Return the new counter value.
     *
     * The EXPIRE is idempotent on subsequent calls (counter > 1) so we only
     * set it once per window, preserving the original window boundary.
     */
    private static final String RATE_LIMIT_LUA =
            "local key = KEYS[1]\n" +
            "local ttl = tonumber(ARGV[1])\n" +
            "local current = redis.call('INCR', key)\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', key, ttl)\n" +
            "end\n" +
            "return current\n";

    private final StringRedisTemplate redis;
    private final RedisScript<Long>   rateLimitScript;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis           = redis;
        this.rateLimitScript = RedisScript.of(RATE_LIMIT_LUA, Long.class);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempts to consume one token from the named bucket.
     *
     * @param tier        rate-limit tier name (e.g. "auth", "api") — used in the Redis key
     * @param clientKey   unique identifier for this client
     * @param limit       maximum requests allowed in the window
     * @param windowSecs  window size in seconds (e.g. 60 for per-minute)
     * @return result indicating whether the request is allowed and how many tokens remain
     */
    @Override
    public RateLimitResult tryConsume(String tier, String clientKey, int limit, int windowSecs) {
        String key = KEY_PREFIX + tier + ":" + clientKey;
        try {
            Long count = redis.execute(rateLimitScript, List.of(key), String.valueOf(windowSecs), String.valueOf(limit));
            if (count == null) count = 1L;

            boolean allowed   = count <= limit;
            long    remaining = Math.max(0L, limit - count);
            long    resetSecs = ttlSeconds(key, windowSecs);

            return new RateLimitResult(allowed, remaining, resetSecs);

        } catch (Exception e) {
            // Redis unavailable — fail-open so a cache outage doesn't down the API
            log.warn("[RATE_LIMIT] Redis unavailable — failing open for tier={} client={}: {}",
                    tier, clientKey, e.getMessage());
            return new RateLimitResult(true, limit - 1L, windowSecs);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long ttlSeconds(String key, int defaultSecs) {
        try {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            return (ttl != null && ttl > 0) ? ttl : defaultSecs;
        } catch (Exception e) {
            return defaultSecs;
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Immutable result from a rate limit check.
     *
     * @param allowed        true if the request should proceed
     * @param remaining      tokens remaining in the current window (0 when blocked)
     * @param resetInSeconds seconds until the current window resets
     */
    public record RateLimitResult(boolean allowed, long remaining, long resetInSeconds) {}
}
