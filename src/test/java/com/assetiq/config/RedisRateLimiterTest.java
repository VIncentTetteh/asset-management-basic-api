package com.assetiq.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisRateLimiter}.
 *
 * All Redis interactions are mocked — no live Redis required. Tests verify:
 *  1. Request is allowed when count ≤ limit.
 *  2. Request is blocked when count > limit.
 *  3. Remaining tokens are calculated correctly.
 *  4. Fail-open behaviour when Redis throws an exception.
 *  5. TTL is forwarded to Redis correctly.
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOps;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rateLimiter = new RedisRateLimiter(redis);
    }

    // ── Happy-path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Allows request when Redis counter is within limit")
    @SuppressWarnings("unchecked")
    void tryConsume_allowedWhenCountWithinLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(3L);  // 3rd request, limit = 5
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(57L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("auth:minute", "client1", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2L);  // 5 - 3
        assertThat(result.resetInSeconds()).isEqualTo(57L);
    }

    @Test
    @DisplayName("Blocks request when Redis counter exceeds limit")
    @SuppressWarnings("unchecked")
    void tryConsume_blockedWhenCountExceedsLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(6L);  // 6th request, limit = 5 — over the limit
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(30L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("auth:minute", "client2", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0L);  // capped at 0, not negative
    }

    @Test
    @DisplayName("Allows first request when Redis counter returns exactly 1")
    @SuppressWarnings("unchecked")
    void tryConsume_allowedOnFirstRequest() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(1L);
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(60L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("api:minute", "client3", 100, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Allows request at exactly the limit boundary")
    @SuppressWarnings("unchecked")
    void tryConsume_allowedAtExactLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(5L);  // 5th request, limit = 5 — exactly at limit
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(10L);

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("auth:minute", "client4", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(0L);
    }

    // ── Fail-open ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fails open when Redis execute throws an exception")
    @SuppressWarnings("unchecked")
    void tryConsume_failsOpenOnRedisException() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("auth:minute", "client5", 5, 60);

        // Must allow the request (fail-open) — never block traffic due to a cache outage
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Uses default TTL when Redis getExpire returns null")
    @SuppressWarnings("unchecked")
    void tryConsume_usesDefaultTtlWhenExpireReturnsNull() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(1L);
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(null);

        RedisRateLimiter.RateLimitResult result = rateLimiter.tryConsume("api:minute", "client6", 100, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.resetInSeconds()).isEqualTo(60L);  // falls back to provided windowSecs
    }

    // ── Key construction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis key includes tier and client key")
    @SuppressWarnings("unchecked")
    void tryConsume_redisKeyIncludesTierAndClient() {
        when(redis.execute(any(RedisScript.class), anyList(), any(String.class), any(String.class)))
                .thenReturn(1L);
        when(redis.getExpire(any(), eq(TimeUnit.SECONDS))).thenReturn(60L);

        rateLimiter.tryConsume("auth:minute", "user@example.com", 5, 60);

        verify(redis).execute(
                any(RedisScript.class),
                argThat(keys -> {
                    String key = ((List<?>) keys).get(0).toString();
                    return key.contains("auth:minute") && key.contains("user@example.com");
                }),
                eq("60"), eq("5"));
    }
}
