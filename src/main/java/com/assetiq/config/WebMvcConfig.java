package com.assetiq.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestCorrelationIdInterceptor correlationIdInterceptor;
    private final ApiAuditInterceptor apiAuditInterceptor;
    private final RateLimitingInterceptor rateLimitingInterceptor;

    public WebMvcConfig(
            RequestCorrelationIdInterceptor correlationIdInterceptor,
            ApiAuditInterceptor apiAuditInterceptor,
            RateLimitingInterceptor rateLimitingInterceptor) {
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.apiAuditInterceptor = apiAuditInterceptor;
        this.rateLimitingInterceptor = rateLimitingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limiting must be first to prevent resource exhaustion
        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/**");

        // Then audit logging
        registry.addInterceptor(apiAuditInterceptor);

        // Finally correlation ID
        registry.addInterceptor(correlationIdInterceptor);
    }
}
