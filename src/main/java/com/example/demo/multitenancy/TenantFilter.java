package com.example.demo.multitenancy;

import com.example.demo.models.User;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.UserRepository;
import io.jsonwebtoken.Claims;
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

        String path = request.getRequestURI();
        log.debug("[TENANT_FILTER] Checking path: {}", path);
        if (path.startsWith("/api/v1/tenant") || path.startsWith("/api/v1/auth") ||
                path.startsWith("/api/v1/billing/webhooks") ||
                path.equals("/api/info") || path.equals("/api/cache/ping") || path.equals("/api/db/hits")) {
            log.debug("[TENANT_FILTER] Skipping for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

        if (isAuthenticated) {
            // Primary: read organisationId from the JWT claim stored by JwtAuthenticationFilter
            // via auth.setDetails(claims). This is O(0) DB calls and is multi-tenant safe —
            // findByEmail alone fails when the same email exists in multiple organisations.
            UUID orgIdFromToken = extractOrgIdFromToken(auth);

            if (orgIdFromToken != null) {
                if (organisationRepository.existsById(orgIdFromToken)) {
                    TenantContext.setOrganisationId(orgIdFromToken);
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Organisation not found or inactive");
                    return;
                }
            } else {
                // Fallback for tokens that pre-date the organisationId claim.
                // Scope the lookup with the X-Organisation-Id header when present to avoid
                // NonUniqueResultException if the same email exists across organisations.
                String email = auth.getName();
                User user = resolveUserFallback(request, email, response);
                if (user == null) {
                    return; // error already written
                }
                if (user.getOrganisation() != null && user.getOrganisation().getId() != null) {
                    UUID orgId = user.getOrganisation().getId();
                    if (organisationRepository.existsById(orgId)) {
                        TenantContext.setOrganisationId(orgId);
                    }
                }
            }
        } else {
            // Unauthenticated requests (e.g. public webhook callbacks): header-based resolution only.
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

        boolean isPublicPath = path.startsWith("/api/v1/tenant") || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/billing/webhooks")
                || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator") || path.startsWith("/webjars")
                || path.startsWith("/error") || path.equals("/");

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

    private UUID extractOrgIdFromToken(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof Claims jwtClaims) {
            String orgIdStr = jwtClaims.get("organisationId", String.class);
            if (orgIdStr != null && !orgIdStr.isBlank()) {
                try {
                    return UUID.fromString(orgIdStr);
                } catch (IllegalArgumentException ignored) {
                    // malformed claim — treat as missing
                }
            }
        }
        return null;
    }

    /**
     * Fallback user lookup for tokens without an organisationId claim.
     * Uses the X-Organisation-Id header to scope the query when possible to avoid
     * NonUniqueResultException in multi-tenant deployments.
     */
    private User resolveUserFallback(HttpServletRequest request, String email,
                                     HttpServletResponse response) throws IOException {
        String orgHeader = request.getHeader(tenantHeader);
        if (orgHeader != null && !orgHeader.isBlank()) {
            try {
                UUID headerOrgId = UUID.fromString(orgHeader.trim());
                User user = userRepository.findByEmailAndOrganisationId(email, headerOrgId).orElse(null);
                if (user != null) return user;
            } catch (IllegalArgumentException ignored) { /* bad header UUID */ }
        }

        try {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                return null;
            }
            return user;
        } catch (Exception e) {
            log.warn("[TENANT_FILTER] Email '{}' exists in multiple organisations. " +
                    "Re-authenticate or provide X-Organisation-Id header.", email);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "User email exists in multiple organisations. Please re-authenticate.");
            return null;
        }
    }
}
