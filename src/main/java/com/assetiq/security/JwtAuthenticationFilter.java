package com.assetiq.security;

import com.assetiq.enums.OrganisationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT authentication filter.
 *
 * Authority loading strategy:
 * Identity and the primary role are resolved from the database on every request.
 * The token selects the user and tenant but cannot keep a disabled account or a
 * stale role alive. Fine-grained permissions are loaded by PermissionCacheService.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final JwtBlacklist jwtBlacklist;
    private final PermissionCacheService permissionCacheService;
    private final boolean requireEmailVerification;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository,
                                   JwtBlacklist jwtBlacklist, PermissionCacheService permissionCacheService,
                                   boolean requireEmailVerification) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.jwtBlacklist = jwtBlacklist;
        this.permissionCacheService = permissionCacheService;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // F-1: resolve token from Authorization header first, then fall back to HttpOnly cookie.
        // This preserves backward-compatibility for API clients and the desktop app while
        // securing browser clients against XSS-based token theft.
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // Reject blacklisted tokens (e.g. tokens invalidated by logout)
                if (jwtBlacklist.isBlacklisted(token)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been invalidated");
                    return;
                }

                Claims claims = jwtUtil.parseToken(token);
                String username = claims.getSubject();
                String orgIdClaim = claims.get("organisationId", String.class);

                User liveUser = resolveActiveUser(username, orgIdClaim).orElse(null);
                if (liveUser == null) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account or tenant is not active");
                    return;
                }
                Number tokenSessionVersion = claims.get("sessionVersion", Number.class);
                if (tokenSessionVersion == null
                        || tokenSessionVersion.longValue() != liveUser.getSessionVersion()) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session has been revoked");
                    return;
                }

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                // Role is read live so role changes take effect immediately. Custom
                // roles do not inherit ROLE_USER; their permissions are explicit.
                if (liveUser.getRole() != null && liveUser.getRole().getName() != null) {
                    String roleStr = liveUser.getRole().getName().trim();
                    if (!roleStr.isEmpty()) {
                        String authority = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                        authorities.add(new SimpleGrantedAuthority(authority));
                    }
                }

                // ── 3. Permission authorities (live from DB / Redis cache) ───────
                // Always load permissions from DB (via PermissionCacheService) so
                // that role permission changes take effect immediately — no re-login
                // required. Results are cached in Redis and evicted by RoleServiceImpl
                // whenever a role's permissions are updated.
                List<String> livePermissions = permissionCacheService.getPermissionsForUser(username, orgIdClaim);
                for (String perm : livePermissions) {
                    if (!perm.isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority(perm));
                    }
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        username, null, authorities);
                auth.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                // Ignore invalid/expired tokens — request continues as unauthenticated
            }
        }
        filterChain.doFilter(request, response);
    }

    private Optional<User> resolveActiveUser(String username, String organisationId) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(organisationId)) {
            return Optional.empty();
        }

        final UUID orgId;
        try {
            orgId = UUID.fromString(organisationId);
        } catch (IllegalArgumentException invalidOrganisationId) {
            return Optional.empty();
        }

        return userRepository.findByEmailAndOrganisationId(username, orgId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> !user.isLockedOut())
                .filter(user -> !requireEmailVerification || user.isEmailVerified())
                .filter(user -> user.getOrganisation() != null)
                .filter(user -> user.getOrganisation().getDeletedAt() == null)
                .filter(user -> user.getOrganisation().getStatus() == OrganisationStatus.ACTIVE);
    }

    /**
     * Resolves the JWT from the request.
     * Priority: Authorization: Bearer header → access_token HttpOnly cookie.
     */
    private static String resolveToken(HttpServletRequest request) {
        // 1. Try Authorization header (API clients, desktop app)
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }

        // 2. Fall back to HttpOnly cookie (browser clients — F-1)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> "access_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
