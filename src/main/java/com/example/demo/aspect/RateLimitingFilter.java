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
        if (authHeader != null && !authHeader.isEmpty()) {
            return authHeader.replace("Bearer ", "").substring(0, Math.min(20, authHeader.length()));
        }

        return request.getRemoteAddr();
    }
}

