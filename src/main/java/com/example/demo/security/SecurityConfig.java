package com.example.demo.security;

import com.example.demo.multitenancy.TenantFilter;
import com.example.demo.repositories.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepository, TenantFilter tenantFilter,
            CorsConfigurationSource corsConfigurationSource) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.tenantFilter = tenantFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtil, userRepository);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((authz) -> authz
                        .requestMatchers("/api/v1/tenant/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/info", "/api/cache/ping", "/api/db/hits").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/error").permitAll()
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
