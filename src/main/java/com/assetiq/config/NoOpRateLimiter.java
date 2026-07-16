package com.assetiq.config;

import org.springframework.stereotype.Component;

/**
 * Fails open when Redis is intentionally absent, such as unit/integration test
 * contexts that exclude Redis auto-configuration.
 */
@Component
public class NoOpRateLimiter implements RateLimiter {

    @Override
    public RedisRateLimiter.RateLimitResult tryConsume(String tier, String clientKey, int limit, int windowSecs) {
        return new RedisRateLimiter.RateLimitResult(true, Math.max(0L, limit - 1L), windowSecs);
    }
}
