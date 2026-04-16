package com.assetiq.security;

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
import java.util.Set;

/**
 * JWT authentication filter.
 *
 * Authority loading strategy:
 *  1. The `role` claim becomes ROLE_<name> (e.g. ROLE_ADMIN, ROLE_USER, ROLE_Manager).
 *  2. Built-in system roles (ROLE_ADMIN, ROLE_ORG_ADMIN, ROLE_USER) are passed through as-is.
 *  3. Custom org roles (anything else) ALSO receive ROLE_USER so that standard
 *     organisation-member endpoints (guarded by ROLE_USER) remain accessible.
 *  4. Every permission string stored in the `permissions` JWT claim is added as its
 *     own GrantedAuthority (e.g. VIEW_ASSETS, CREATE_ASSET) so that fine-grained
 *     @PreAuthorize("hasAuthority('VIEW_ASSETS')") checks work.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Roles that are part of the built-in hierarchy — not treated as custom org roles. */
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_ORG_ADMIN", "ROLE_USER"
    );

    private final JwtUtil jwtUtil;
    private final JwtBlacklist jwtBlacklist;
    private final PermissionCacheService permissionCacheService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository,
                                   JwtBlacklist jwtBlacklist, PermissionCacheService permissionCacheService) {
        this.jwtUtil = jwtUtil;
        this.jwtBlacklist = jwtBlacklist;
        this.permissionCacheService = permissionCacheService;
        // userRepository retained in constructor signature for backward-compat with SecurityConfig
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

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                // ── 1. Role authority ─────────────────────────────────────────────
                Object roleClaim = claims.get("role");
                if (roleClaim != null) {
                    String roleStr = roleClaim.toString().trim();
                    if (!roleStr.isEmpty()) {
                        String authority = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                        authorities.add(new SimpleGrantedAuthority(authority));

                        // ── 2. Custom-role fallback ───────────────────────────────
                        // Users with a custom org role (e.g. ROLE_Manager) must also
                        // receive ROLE_USER so that standard member endpoints work.
                        if (!SYSTEM_ROLES.contains(authority)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        }
                    }
                }

                // ── 3. Permission authorities (live from DB / Redis cache) ───────
                // Always load permissions from DB (via PermissionCacheService) so
                // that role permission changes take effect immediately — no re-login
                // required. Results are cached in Redis and evicted by RoleServiceImpl
                // whenever a role's permissions are updated.
                String orgIdClaim = claims.get("organisationId", String.class);
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
