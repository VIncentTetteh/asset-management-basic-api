package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    @Value("${app.cors.allow-localhost:false}")
    private boolean allowLocalhost;

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse and validate allowed origins
        List<String> allowedOrigins = parseAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                "CORS allowed-origins must be configured. Set via environment variable: " +
                "export SPRING_APPLICATION_JSON=" +
                "'{\"app\":{\"cors\":{\"allowed-origins\":\"https://example.com\"}}}'");
        }

        configuration.setAllowedOrigins(allowedOrigins);

        // Strict HTTP method allowlist
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Explicit header allowlist (deny * with credentials)
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With",
            "X-Client-ID",
            "X-Request-ID",
            "X-Organisation-Id",
            "Accept",
            "Accept-Language"
        ));

        // Expose only the necessary headers
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "X-Request-ID"
        ));

        // Allow credentials ONLY with explicit origins (not wildcard)
        configuration.setAllowCredentials(true);

        // Reduce the preflight cache from 3600 to 300 seconds (5 minutes)
        configuration.setMaxAge(300L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    private List<String> parseAllowedOrigins() {
        if (allowedOriginsConfig == null || allowedOriginsConfig.isBlank()) {
            return List.of();  // No CORS if not configured
        }

        String[] origins = allowedOriginsConfig.split(",");
        return Arrays.stream(origins)
                .map(String::trim)
                .filter(this::isValidOrigin)
                .toList();
    }

    private boolean isValidOrigin(String origin) {
        // Reject dangerous origins
        if (origin.contains("*")) {
            throw new IllegalStateException(
                "Invalid CORS origin: " + origin + ". " +
                "Wildcards are not allowed. Use explicit origins only.");
        }
        
        boolean isDev = isDevProfile();
        boolean isLocal = origin.toLowerCase(Locale.ROOT).contains("localhost")
                || origin.contains("127.0.0.1");

        if (isLocal && !(isDev || allowLocalhost)) {
            throw new IllegalStateException(
                "Invalid CORS origin: " + origin + ". " +
                "localhost/127.0.0.1 are not allowed in production. " +
                "Set SPRING_PROFILES_ACTIVE=dev or app.cors.allow-localhost=true if needed.");
        }
        return true;
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("dev") || p.equals("local") || p.equals("test"));
    }
}
