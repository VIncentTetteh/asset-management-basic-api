package com.assetiq.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    // Strict limits for auth endpoints
    private static final int AUTH_REQUESTS_PER_MINUTE = 5;
    private static final int AUTH_REQUESTS_PER_HOUR = 50;

    // General API limit
    private static final int API_REQUESTS_PER_MINUTE = 100;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, @NotNull Object handler)
            throws IOException {

        String path = request.getRequestURI();
        String clientKey = getClientIdentifier(request);

        // Determine if this is an auth endpoint
        boolean isAuthEndpoint = path.startsWith("/api/v1/auth");
        int limit = isAuthEndpoint ? AUTH_REQUESTS_PER_MINUTE : API_REQUESTS_PER_MINUTE;

        // Get or create bucket for this client
        Bucket bucket = RateLimitingConfig.resolveBucket(clientKey);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Add rate limit headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (!probe.isConsumed()) {
            long waitNanos = probe.getNanosToWaitForRefill();
            long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(waitNanos);
            response.setHeader("X-RateLimit-Reset", 
                    String.valueOf(System.currentTimeMillis() / 1000 + waitSeconds));
            response.setHeader("Retry-After", String.valueOf(waitSeconds));

            log.warn("[RATE_LIMIT] Client {} exceeded limit for path {}. Retry after {} seconds",
                    clientKey, path, waitSeconds);

            response.sendError(429,
                    "Rate limit exceeded. Please retry after " + waitSeconds + " seconds.");
            return false;
        }

        return true;
    }

    /**
     * Extract client identifier from request.
     * Prefers X-Forwarded-For header (behind proxy) over remote address.
     */
    private String getClientIdentifier(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take first IP if multiple are present
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}



