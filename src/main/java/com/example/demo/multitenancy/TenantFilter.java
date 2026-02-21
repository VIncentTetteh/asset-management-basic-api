package com.example.demo.multitenancy;

import com.example.demo.models.User;
import com.example.demo.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Value("${app.tenant.header:X-Organisation-Id}")
    private String tenantHeader;

    private final UserRepository userRepository;

    public TenantFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // If user is authenticated, do a DB-backed lookup to determine tenant
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
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
                if (user.getOrganisation() != null) {
                    TenantContext.setOrganisationId(user.getOrganisation().getId());
                }
            }
        }

        // if tenant still not set from user lookup, fall back to header
        if (!TenantContext.hasOrganisationId()) {
            String header = request.getHeader(tenantHeader);
            if (header != null && !header.isBlank()) {
                try {
                    UUID orgId = UUID.fromString(header.trim());
                    TenantContext.setOrganisationId(orgId);
                } catch (IllegalArgumentException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid organisation id in header");
                    return;
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
