package com.example.demo.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed JWT blacklist.
 * Invalidated tokens (e.g. on logout) are stored in Redis with TTL = remaining token lifetime.
 * The JwtAuthenticationFilter checks this before accepting any token.
 */
@Component
public class JwtBlacklist {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklist.class);
    private static final String PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public JwtBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Blacklist a token by its JTI (or full token string) for the given duration.
     * After TTL expires, Redis automatically removes the entry.
     */
    public void invalidate(String tokenId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + tokenId, "1", ttl);
            log.debug("[JWT_BLACKLIST] Token invalidated: {}", tokenId.substring(0, Math.min(12, tokenId.length())));
        } catch (Exception e) {
            log.error("[JWT_BLACKLIST] Failed to invalidate token in Redis", e);
        }
    }

    /**
     * Returns true if the given token ID has been blacklisted.
     */
    public boolean isBlacklisted(String tokenId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + tokenId));
        } catch (Exception e) {
            log.error("[JWT_BLACKLIST] Failed to check blacklist in Redis — allowing request to proceed", e);
            // Fail open: don't block users if Redis is temporarily unavailable
            return false;
        }
    }
}
