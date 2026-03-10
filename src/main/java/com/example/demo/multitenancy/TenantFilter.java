package com.example.demo.multitenancy;

import com.example.demo.models.User;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    @Value("${app.tenant.header:X-Organisation-Id}")
    private String tenantHeader;

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    public TenantFilter(UserRepository userRepository, OrganisationRepository organisationRepository) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip for public registration path
        String path = request.getRequestURI();
        log.debug("[TENANT_FILTER] Checking path: {}", path);
        if (path.startsWith("/api/v1/tenant") || path.startsWith("/api/v1/auth") ||
                path.startsWith("/api/v1/billing/webhooks") ||
                path.equals("/api/info") || path.equals("/api/cache/ping") || path.equals("/api/db/hits")) {
            log.debug("[TENANT_FILTER] Skipping for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // If user is authenticated and not anonymous, do a DB-backed lookup
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        if (isAuthenticated) {
            Object principal = auth.getPrincipal();
            String username = null;
            if (principal instanceof String) {
                username = (String) principal;
            } else if (principal != null) {
                username = principal.toString();
            }

            if (username != null) {
                User user = userRepository.findByEmail(username).orElse(null);
                if (user == null) {
                    // Token valid but user no longer exists
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                    return;
                }
                if (user.getOrganisation() != null && user.getOrganisation().getId() != null) {
                    UUID orgId = user.getOrganisation().getId();
                    if (organisationRepository.existsById(orgId)) {
                        TenantContext.setOrganisationId(orgId);
                    }
                }
            }
            // For authenticated users, NEVER fall back to header-based tenant resolution.
            // If org could not be resolved from the user's account, deny the request below.
        } else {
            // For non-authenticated requests (e.g. public webhook callbacks), allow header-based resolution.
            String header = request.getHeader(tenantHeader);
            if (header != null && !header.isBlank()) {
                try {
                    UUID orgId = UUID.fromString(header.trim());
                    if (organisationRepository.existsById(orgId)) {
                        TenantContext.setOrganisationId(orgId);
                    }
                } catch (IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid organisation id in header");
                    return;
                }
            }
        }

        // For authenticated requests to protected paths: require a resolved tenant
        boolean isPublicPath = path.startsWith("/api/v1/tenant") || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/billing/webhooks")
                || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");

        if (isAuthenticated && !isPublicPath && !TenantContext.hasOrganisationId()) {
            log.warn("[TENANT_FILTER] Tenant context could not be resolved for authenticated request to {}", path);
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "No organisation context resolved for your account. Contact your administrator.");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
