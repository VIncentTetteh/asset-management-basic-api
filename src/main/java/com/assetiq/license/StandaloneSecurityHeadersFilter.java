package com.assetiq.license;

import com.assetiq.config.ConditionalOnAppMode;
import com.assetiq.config.AppMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * StandaloneSecurityHeadersFilter — Phase 6
 *
 * Adds security-hardening HTTP response headers that are appropriate for an
 * on-premise installation.  This filter is ONLY loaded in standalone mode
 * ({@code APP_MODE=standalone}); it is completely absent from the cloud app.
 *
 * Headers added:
 *   Strict-Transport-Security — enforces HTTPS for 1 year (include subdomains)
 *   X-Content-Type-Options    — prevents MIME sniffing
 *   X-Frame-Options           — blocks clickjacking
 *   X-XSS-Protection          — legacy XSS filter (belt + suspenders)
 *   Referrer-Policy           — limits referrer leakage
 *   Permissions-Policy        — disables unused browser features
 *   Content-Security-Policy   — tight policy; API responses are data, not HTML
 *
 * nginx already sets these on the reverse-proxy layer; the filter is a
 * defence-in-depth measure for direct access to port 8080.
 */
@Component
@ConditionalOnAppMode(AppMode.STANDALONE)
@Order(1)   // run before JWT filter so every response — including 401 — gets the headers
public class StandaloneSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // HTTPS enforcement — 1 year, include subdomains, allow preload
        response.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Block framing (clickjacking)
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS filter — still respected by older browsers
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Only send origin as referrer on same-origin requests
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Disable features not used by the API
        response.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=()");

        // API responses are JSON — no scripts, no frames, no external resources
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'");

        chain.doFilter(request, response);
    }
}
