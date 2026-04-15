package com.assetiq.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    /**
     * API Versioning Strategy
     *
     * This configuration supports URL-based API versioning:
     * - /api/v1/... (current version)
     * - /api/v2/... (future version)
     *
     * Example endpoints:
     * - GET /api/v1/assets (v1)
     * - GET /api/v2/assets (v2 - future)
     *
     * Benefits:
     * 1. Clear version separation
     * 2. Backward compatibility
     * 3. Gradual migration path
     * 4. Easy deprecation
     *
     * Alternative strategies:
     * - Header-based: X-API-Version header
     * - Query parameter: ?version=1
     * - Content negotiation: application/vnd.api+v1+json
     */

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        urlPathHelper.setRemoveSemicolonContent(true);
        configurer.setUrlPathHelper(urlPathHelper);
    }
}

