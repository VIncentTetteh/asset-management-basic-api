package com.example.demo.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting config backed by a bounded, expiring map (avoids the unbounded
 * ConcurrentHashMap memory leak from the previous implementation).
 *
 * Uses a simple size-bounded LinkedHashMap with LRU eviction as a Caffeine-free
 * alternative. For production with many unique clients, replace the bucketCache
 * with a proper Caffeine cache (add com.github.ben-manes.caffeine:caffeine if
 * it is not already on the classpath via spring-boot-starter-cache).
 */
@Configuration
public class RateLimitingConfig {

    private static final int MAX_BUCKETS = 50_000;

    @SuppressWarnings("serial")
    private static final java.util.Map<String, BucketEntry> bucketCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, BucketEntry>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, BucketEntry> eldest) {
                    // Evict oldest entries AND entries older than 1 hour
                    if (size() > MAX_BUCKETS)
                        return true;
                    return (System.currentTimeMillis() - eldest.getValue().createdAt) > TimeUnit.HOURS.toMillis(1);
                }
            });

    private record BucketEntry(Bucket bucket, long createdAt) {
    }

    public static Bucket resolveBucket(String clientKey) {
        return bucketCache.computeIfAbsent(clientKey,
                k -> new BucketEntry(createBucket(), System.currentTimeMillis())).bucket();
    }

    private static Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public static class RateLimitConfig {
        public static final int REQUESTS_PER_MINUTE = 100;
        public static final int REQUESTS_PER_HOUR = 5000;
        public static final String RATE_LIMIT_HEADER = "X-RateLimit-Remaining";
        public static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    }
}
