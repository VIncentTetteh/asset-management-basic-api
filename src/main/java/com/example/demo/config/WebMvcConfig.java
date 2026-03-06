package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestCorrelationIdInterceptor correlationIdInterceptor;
    private final ApiAuditInterceptor apiAuditInterceptor;

    public WebMvcConfig(RequestCorrelationIdInterceptor correlationIdInterceptor,
            ApiAuditInterceptor apiAuditInterceptor) {
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.apiAuditInterceptor = apiAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor);
        registry.addInterceptor(apiAuditInterceptor);
    }
}
