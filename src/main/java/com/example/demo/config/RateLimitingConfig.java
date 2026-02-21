package com.example.demo.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitingConfig {

    private static final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public static Bucket resolveBucket(String clientKey) {
        return buckets.computeIfAbsent(clientKey, k -> createBucket());
    }

    private static Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Get rate limit configuration:
     * 100 requests per minute per API key/client
     * Can be customized per endpoint
     */
    public static class RateLimitConfig {
        public static final int REQUESTS_PER_MINUTE = 100;
        public static final int REQUESTS_PER_HOUR = 5000;
        public static final String RATE_LIMIT_HEADER = "X-RateLimit-Remaining";
        public static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    }
}

