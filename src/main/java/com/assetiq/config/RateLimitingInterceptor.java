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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static com.assetiq.config.RateLimitingConfig.*;

/**
 * Enforces per-client rate limits at the Spring MVC interceptor layer.
 *
 * Auth endpoints (/api/v1/auth/**, /api/v1/mfa/**) use a strict two-tier
 * bucket (5 req/min, 20 req/hour) to block brute-force attacks.
 *
 * All other /api/** paths use the relaxed general bucket (100 req/min).
 *
 * Standard RFC 6585 / draft-ietf-httpapi-ratelimit-headers response headers
 * are added to every response so clients can back off gracefully:
 *   X-RateLimit-Limit     — capacity of the current window
 *   X-RateLimit-Remaining — tokens left in the current window
 *   X-RateLimit-Reset     — Unix epoch seconds when the window resets
 *   Retry-After           — seconds to wait before retrying (only on 429)
 *
 * NOTE: RateLimitingFilter (the Servlet-layer OncePerRequestFilter) has been
 * @SuppressWarnings disabled from the filter chain (see WebMvcConfig) so that
 * rate limiting is applied exactly once, here.
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    /** Path prefixes that get the strict auth-tier bucket. */
    private static final String[] AUTH_PREFIXES = {"/api/v1/auth", "/api/v1/mfa"};

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) throws IOException {

        String path       = request.getRequestURI();
        String clientKey  = extractClientKey(request);
        boolean isAuth    = isAuthPath(path);

        Bucket bucket     = isAuth ? resolveAuthBucket(clientKey) : resolveGeneralBucket(clientKey);
        int    limit      = isAuth ? AUTH_REQUESTS_PER_MINUTE : API_REQUESTS_PER_MINUTE;

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Always set informational headers
        response.setHeader(HEADER_LIMIT,     String.valueOf(limit));
        response.setHeader(HEADER_REMAINING, String.valueOf(Math.max(0L, probe.getRemainingTokens())));

        if (probe.isConsumed()) {
            return true;
        }

        // Token exhausted — compute how long the client must wait
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        long resetEpoch  = System.currentTimeMillis() / 1000L + waitSeconds;

        response.setHeader(HEADER_RESET,       String.valueOf(resetEpoch));
        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(waitSeconds));

        log.warn("[RATE_LIMIT] client={} path={} tier={} retryAfter={}s",
                clientKey, path, isAuth ? "AUTH" : "API", waitSeconds);

        response.sendError(429, "Rate limit exceeded. Retry after " + waitSeconds + " second(s).");
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthPath(String path) {
        if (path == null) return false;
        for (String prefix : AUTH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Derives a stable, non-guessable client key:
     * 1. X-Client-ID header (explicit SDK / mobile clients)
     * 2. SHA-256 prefix of the Bearer token (unique per user session, not reversible)
     * 3. X-Forwarded-For first IP (behind a reverse proxy)
     * 4. Direct remote address (fallback)
     */
    private String extractClientKey(HttpServletRequest request) {
        // 1. Explicit client ID
        String clientId = request.getHeader("X-Client-ID");
        if (clientId != null && !clientId.isBlank()) {
            return "cid:" + clientId;
        }

        // 2. Hashed Bearer token — unique per authenticated session
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
                // Use first 8 bytes (16 hex chars) — collision probability negligible
                StringBuilder sb = new StringBuilder(16);
                for (int i = 0; i < 8; i++) {
                    sb.append(String.format("%02x", hash[i]));
                }
                return "jwt:" + sb;
            } catch (NoSuchAlgorithmException ex) {
                // SHA-256 is always present in a Java 8+ JRE; unreachable in practice
                int len = token.length();
                return "jwt:" + token.substring(Math.max(0, len - 16));
            }
        }

        // 3 & 4. IP address
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
