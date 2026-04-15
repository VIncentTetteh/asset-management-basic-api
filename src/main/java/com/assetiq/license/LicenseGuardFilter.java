package com.assetiq.license;

import com.assetiq.config.AppMode;
import com.assetiq.config.ConditionalOnAppMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Blocks write operations when the license is in read-only state.
 *
 * <p><strong>Only instantiated when {@code APP_MODE=standalone}.</strong>
 * In cloud mode this filter class is never loaded into the Spring context,
 * so cloud behaviour is completely unchanged.</p>
 *
 * <p>Read-only enforcement rules:</p>
 * <ul>
 *   <li>GET, HEAD, OPTIONS — always allowed (read access preserved)</li>
 *   <li>POST, PUT, PATCH, DELETE — blocked with HTTP 402 when license is read-only</li>
 *   <li>Paystack webhook, auth, and health endpoints — exempt even in read-only mode</li>
 * </ul>
 */
@Component
@ConditionalOnAppMode(AppMode.STANDALONE)
@Order(10)
public class LicenseGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LicenseGuardFilter.class);

    private static final Set<String> WRITE_METHODS = Set.of(
        HttpMethod.POST.name(), HttpMethod.PUT.name(),
        HttpMethod.PATCH.name(), HttpMethod.DELETE.name()
    );

    /** Endpoints that must remain writable even when the license is read-only. */
    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/logout",
        "/api/v1/auth/refresh",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/mfa/challenge",
        "/api/v1/billing/webhooks/paystack",
        "/api/v1/license/activate",   // must be writable so admin can enter a new key
        "/actuator/health"
    );

    private final LicenseService licenseService;
    private final ObjectMapper   objectMapper;

    public LicenseGuardFilter(LicenseService licenseService, ObjectMapper objectMapper) {
        this.licenseService = licenseService;
        this.objectMapper   = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String method = req.getMethod();
        String path   = req.getRequestURI();

        if (WRITE_METHODS.contains(method) && !isExempt(path)) {
            LicenseState state = licenseService.getCurrentState();
            if (state.readOnly()) {
                log.debug("License read-only guard blocked {} {}", method, path);
                res.setStatus(402);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "status",  402,
                    "error",   "LICENSE_READ_ONLY",
                    "message", state.message() != null
                        ? state.message()
                        : "Your license has expired. Renew at https://portal.assetiq.io",
                    "licenseStatus", state.status()
                )));
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean isExempt(String path) {
        return EXEMPT_PATHS.stream().anyMatch(path::startsWith);
    }
}
