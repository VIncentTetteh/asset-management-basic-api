package com.assetiq.aspect;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.assetiq.config.RateLimitingConfig.HEADER_REMAINING;
import static com.assetiq.config.RateLimitingConfig.HEADER_RETRY_AFTER;
import static com.assetiq.config.RateLimitingConfig.API_REQUESTS_PER_MINUTE;

/**
 * Servlet-layer rate limiting filter — standalone / fallback implementation.
 *
 * <p><b>NOTE: {@code @Component} is intentionally absent.</b>
 * Rate limiting in the main deployment is enforced by the Spring MVC interceptor
 * {@link com.assetiq.config.RateLimitingInterceptor}, which is Redis-backed and
 * therefore effective across all horizontally scaled instances.  Registering this
 * filter as a Spring bean would create a duplicate, in-memory enforcement layer.</p>
 *
 * <p>This class is kept for two purposes:</p>
 * <ol>
 *   <li>Standalone / embedded mode where a Spring context may not be available.</li>
 *   <li>Integration tests that need Servlet-level rate limiting without the full
 *       Redis stack.</li>
 * </ol>
 *
 * <p>The in-memory counter is intentionally simple — a per-minute sliding window
 * using a ConcurrentHashMap.  It is NOT shared across JVM instances.</p>
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Simple per-client counter: clientKey → [requestCount, windowStartMillis] */
    private final Map<String, long[]> windowMap = new ConcurrentHashMap<>();

    private final int  limitPerMinute;
    private final long windowMillis;

    public RateLimitingFilter() {
        this(API_REQUESTS_PER_MINUTE, 60_000L);
    }

    public RateLimitingFilter(int limitPerMinute, long windowMillis) {
        this.limitPerMinute = limitPerMinute;
        this.windowMillis   = windowMillis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientKey = getClientKey(request);
        long   now       = System.currentTimeMillis();

        long[] state  = windowMap.computeIfAbsent(clientKey, k -> new long[]{0L, now});
        long remaining;

        synchronized (state) {
            // Reset window if expired
            if (now - state[1] >= windowMillis) {
                state[0] = 0L;
                state[1] = now;
            }
            state[0]++;
            remaining = Math.max(0L, limitPerMinute - state[0]);

            if (state[0] > limitPerMinute) {
                long retryAfterSecs = (windowMillis - (now - state[1])) / 1000L;
                response.addHeader(HEADER_REMAINING,   "0");
                response.addHeader(HEADER_RETRY_AFTER, String.valueOf(Math.max(1L, retryAfterSecs)));
                response.sendError(429, "Too many requests. Please retry after " +
                                        Math.max(1L, retryAfterSecs) + " second(s).");
                return;
            }
        }

        response.addHeader(HEADER_REMAINING, String.valueOf(remaining));
        filterChain.doFilter(request, response);
    }

    private String getClientKey(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-ID");
        if (clientId != null && !clientId.isEmpty()) {
            return "cid:" + clientId;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return "jwt:" + sha256Prefix(authHeader.substring(7));
        }

        return "ip:" + request.getRemoteAddr();
    }

    private String sha256Prefix(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[]        hash   = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb     = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            int len = token.length();
            return token.substring(Math.max(0, len - 16));
        }
    }
}
