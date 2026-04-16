package com.assetiq.aspect;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.assetiq.config.RateLimitingConfig.resolveGeneralBucket;
import static com.assetiq.config.RateLimitingConfig.HEADER_REMAINING;
import static com.assetiq.config.RateLimitingConfig.HEADER_RETRY_AFTER;

/**
 * Servlet-layer rate limiting filter.
 *
 * NOTE: @Component is intentionally absent.  Rate limiting is enforced at the
 * Spring MVC interceptor level by {@link com.assetiq.config.RateLimitingInterceptor},
 * which applies the correct per-tier bucket (auth vs. general) and emits all
 * standard rate-limit response headers.  Registering this filter as a Spring
 * bean would create a duplicate enforcement layer that consumes two tokens per
 * request from the same bucket.
 *
 * This class is kept for reference / standalone-mode use only.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientKey = getClientKey(request);
        Bucket bucket = resolveGeneralBucket(clientKey);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader(HEADER_REMAINING, String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.addHeader(HEADER_RETRY_AFTER, "60");
            response.sendError(429, "Too many requests. Please retry after 60 seconds.");
        }
    }

    private String getClientKey(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-ID");
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                // Hash the token so the bucket key is unique per user but not guessable
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(16);
                for (int i = 0; i < 8; i++) {
                    sb.append(String.format("%02x", hash[i]));
                }
                return "jwt:" + sb;
            } catch (NoSuchAlgorithmException e) {
                // Fallback: use last 16 chars of token (signature portion, unique per user)
                int len = token.length();
                return "jwt:" + token.substring(Math.max(0, len - 16));
            }
        }

        return request.getRemoteAddr();
    }
}

