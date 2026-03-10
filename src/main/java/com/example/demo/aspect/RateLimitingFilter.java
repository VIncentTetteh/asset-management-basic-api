package com.example.demo.aspect;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.example.demo.config.RateLimitingConfig.resolveBucket;
import static com.example.demo.config.RateLimitingConfig.RateLimitConfig.*;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientKey = getClientKey(request);
        Bucket bucket = resolveBucket(clientKey);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader(RATE_LIMIT_HEADER, String.valueOf(probe.getRemainingTokens()));
            // Probe consumed successfully
            filterChain.doFilter(request, response);
        } else {
            // Probe not consumed - rate limit exceeded
            // For now, suggest 60 seconds wait time
            response.addHeader("X-RateLimit-Retry-After-Seconds", "60");
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

