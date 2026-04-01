package com.example.demo.security;

import com.example.demo.multitenancy.TenantFilter;
import com.example.demo.repositories.UserRepository;
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

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TenantFilter tenantFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtBlacklist jwtBlacklist;

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepository, TenantFilter tenantFilter,
            CorsConfigurationSource corsConfigurationSource, JwtBlacklist jwtBlacklist) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtBlacklist = jwtBlacklist;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userRepository, jwtBlacklist);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((authz) -> authz
                        // ── Swagger / OpenAPI ──────────────────────────────────────────────
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()

                        // ── Tenant registration ────────────────────────────────────────────
                        .requestMatchers("/api/v1/tenant/**").permitAll()

                        // ── Auth: only truly-public endpoints are permit-all ───────────────
                        // login, register, password reset — no token needed
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                        // SSO callbacks and initiation — called by external IdP or before login
                        .requestMatchers("/api/v1/auth/sso/**").permitAll()
                        // /auth/profile, /auth/refresh, /auth/logout remain AUTHENTICATED (see anyRequest below)

                        // ── MFA challenge — called during login before a full JWT is issued ──
                        .requestMatchers(HttpMethod.POST, "/api/v1/mfa/challenge").permitAll()

                        // ── Billing ────────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/paystack").permitAll()

                        // ── Internal / infrastructure ──────────────────────────────────────
                        .requestMatchers("/api/info", "/api/cache/ping", "/api/db/hits").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/health", "/api/v1/health/**").permitAll()

                        // ── All other requests require a valid JWT ─────────────────────────
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
