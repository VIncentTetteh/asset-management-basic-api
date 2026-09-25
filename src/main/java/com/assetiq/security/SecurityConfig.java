package com.assetiq.security;

import com.assetiq.license.LicenseGuardFilter;
import com.assetiq.multitenancy.TenantFilter;
import com.assetiq.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
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
    private final BrowserMutationOriginFilter browserMutationOriginFilter;

    @Value("${app.auth.require-email-verification:true}")
    private boolean requireEmailVerification;

    /**
     * Only present when APP_MODE=standalone (annotated with @ConditionalOnAppMode).
     * In cloud mode this is Optional.empty() and the filter is never added to the chain.
     */
    private final Optional<LicenseGuardFilter> licenseGuardFilter;

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepository, TenantFilter tenantFilter,
            CorsConfigurationSource corsConfigurationSource, JwtBlacklist jwtBlacklist,
            PermissionCacheService permissionCacheService,
            BrowserMutationOriginFilter browserMutationOriginFilter,
            Optional<LicenseGuardFilter> licenseGuardFilter) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtBlacklist = jwtBlacklist;
        this.permissionCacheService = permissionCacheService;
        this.browserMutationOriginFilter = browserMutationOriginFilter;
        this.licenseGuardFilter = licenseGuardFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                jwtUtil, userRepository, jwtBlacklist, permissionCacheService, requireEmailVerification);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // Cookie browser mutations are CSRF-protected by the strict origin filter;
            // bearer-only mobile/desktop/API requests remain stateless.
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> {
                exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
                AccessDeniedHandlerImpl denied = new AccessDeniedHandlerImpl();
                denied.setErrorPage(null);
                exceptions.accessDeniedHandler(denied);
            })
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
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                // Necessarily public: the user cannot sign in until they have verified.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification").permitAll()
                // Refresh/logout authenticate using rotating opaque refresh sessions.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
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
                // Includes the probe sub-paths /actuator/health/liveness and
                // /actuator/health/readiness. Matching only "/actuator/health" left both
                // returning 403 to an unauthenticated caller, which is exactly what a load
                // balancer or orchestrator is: the readiness probe could never pass, so an
                // instance would never be marked healthy. Detail stays protected
                // regardless - management.endpoint.health.show-details is when-authorized,
                // so an anonymous caller sees only the aggregate status.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/v1/health", "/api/v1/health/detailed", "/api/v1/metrics/**", "/api/v1/metrics")
                    .hasAuthority("ROLE_ADMIN")

                // ── All other requests require a valid JWT ─────────────────────────
                .anyRequest().authenticated())
            .addFilterBefore(browserMutationOriginFilter, UsernamePasswordAuthenticationFilter.class)
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
