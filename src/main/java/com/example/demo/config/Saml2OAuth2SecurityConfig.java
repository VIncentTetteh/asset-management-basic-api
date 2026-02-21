package com.example.demo.config;

import org.springframework.context.annotation.Configuration;

/**
 * SAML2 / OAuth2 Security Configuration (disabled)
 *
 * This configuration is intentionally minimal and disabled by default in favor of
 * JWT-based authentication configured elsewhere (e.g., SecurityConfig).
 *
 * To enable SAML2/OAuth2 in the future, implement the necessary beans here and
 * configure the properties in application.properties.
 */
@Configuration
public class Saml2OAuth2SecurityConfig {
    // No active beans. SAML2/OAuth2 setup is currently disabled.
}

