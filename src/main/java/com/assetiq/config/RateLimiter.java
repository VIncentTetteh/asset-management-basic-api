package com.assetiq.config;

/**
 * Small abstraction for rate-limit storage so tests and non-Redis local runs can
 * boot without constructing Redis infrastructure.
 */
public interface RateLimiter {
    RedisRateLimiter.RateLimitResult tryConsume(String tier, String clientKey, int limit, int windowSecs);
}
