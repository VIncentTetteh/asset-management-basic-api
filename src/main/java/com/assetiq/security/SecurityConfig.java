package com.assetiq.security;

import com.assetiq.license.LicenseGuardFilter;
import com.assetiq.multitenancy.TenantFilter;
import com.assetiq.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Optional;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TenantFilter tenantFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtBlacklist jwtBlacklist;
    private final PermissionCacheService permissionCacheService;

    /**
     * Only present when APP_MODE=standalone (annotated with @ConditionalOnAppMode).
     * In cloud mode this is Optional.empty() and the filter is never added to the chain.
     */
    private final Optional<LicenseGuardFilter> licenseGuardFilter;

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepository, TenantFilter tenantFilter,
            CorsConfigurationSource corsConfigurationSource, JwtBlacklist jwtBlacklist,
            PermissionCacheService permissionCacheService,
            Optional<LicenseGuardFilter> licenseGuardFilter) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtBlacklist = jwtBlacklist;
        this.permissionCacheService = permissionCacheService;
        this.licenseGuardFilter = licenseGuardFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userRepository, jwtBlacklist, permissionCacheService);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((authz) -> authz
                // ── Swagger / OpenAPI ──────────────────────────────────────────────
                // Restrict Swagger UI and OpenAPI docs to admin roles only
                .requestMatchers("/swagger-ui.html").hasAnyAuthority("ROLE_ADMIN", "ROLE_ORG_ADMIN")
                .requestMatchers("/swagger-ui/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_ORG_ADMIN")
                .requestMatchers("/v3/api-docs/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_ORG_ADMIN")
                .requestMatchers("/webjars/**").permitAll()

                // ── Tenant registration ────────────────────────────────────────────
                .requestMatchers("/api/v1/tenant/**").permitAll()

                // ── Auth: only truly-public endpoints are permit-all ───────────────
                // login, register, password reset — no token needed
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                // Necessarily public: the user cannot sign in until they have verified.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification").permitAll()
                // SSO callbacks and initiation — called by external IdP or before login
                .requestMatchers("/api/v1/auth/sso/**").permitAll()
                // /auth/profile, /auth/refresh, /auth/logout remain AUTHENTICATED (see anyRequest below)

                // ── MFA challenge — called during login before a full JWT is issued ──
                .requestMatchers(HttpMethod.POST, "/api/v1/mfa/challenge").permitAll()

                // ── Billing ────────────────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/paystack").permitAll()

                // ── License (status is public so frontend can poll without auth) ───
                .requestMatchers(HttpMethod.GET, "/api/v1/license/status").permitAll()

                // ── Internal / infrastructure ──────────────────────────────────────
                .requestMatchers("/api/info", "/api/cache/ping", "/api/db/hits").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/health/detailed", "/api/v1/metrics/**", "/api/v1/metrics")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_ORG_ADMIN")

                // ── All other requests require a valid JWT ─────────────────────────
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantFilter, JwtAuthenticationFilter.class);

        // LicenseGuardFilter runs AFTER tenant resolution (it may need org context).
        // Only added to the chain when APP_MODE=standalone — Optional.empty() in cloud mode.
        licenseGuardFilter.ifPresent(f ->
            http.addFilterAfter(f, TenantFilter.class)
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
