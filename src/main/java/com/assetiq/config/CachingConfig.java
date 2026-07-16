package com.assetiq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

// @EnableCaching is on AssetIQApplication so it is always active regardless of Redis.
// This class only wires the Redis-specific CacheManager when Redis is available.
@Configuration
public class CachingConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Cache configuration constants
     */
    public static class CacheNames {
        // Asset caches
        public static final String ASSETS = "assets";
        public static final String ASSET_BY_ID = "asset_by_id";
        public static final String ASSETS_BY_DEPARTMENT = "assets_by_department";
        public static final String ASSETS_BY_LOCATION = "assets_by_location";
        public static final String ASSETS_BY_STATUS = "assets_by_status";

        // Organization caches
        public static final String ORGANISATIONS = "organisations";
        public static final String ORGANISATION_BY_ID = "organisation_by_id";

        // Department caches
        public static final String DEPARTMENTS = "departments";
        public static final String DEPARTMENT_BY_ID = "department_by_id";

        // User caches
        public static final String USERS = "users";
        public static final String USER_BY_ID = "user_by_id";
        public static final String USER_BY_EMAIL = "user_by_email";

        // Role caches
        public static final String ROLES = "roles";
        public static final String ROLE_BY_ID = "role_by_id";
        public static final String ROLE_BY_NAME = "role_by_name";

        // Category caches
        public static final String CATEGORIES = "categories";
        public static final String CATEGORY_BY_ID = "category_by_id";

        // Location caches
        public static final String LOCATIONS = "locations";
        public static final String LOCATION_BY_ID = "location_by_id";

        // Supplier caches
        public static final String SUPPLIERS = "suppliers";
        public static final String SUPPLIER_BY_ID = "supplier_by_id";

        // Maintenance caches
        public static final String MAINTENANCE_RECORDS = "maintenance_records";
        public static final String MAINTENANCE_BY_ASSET = "maintenance_by_asset";

        // Audit caches
        public static final String AUDITS = "audits";
        public static final String AUDIT_BY_ID = "audit_by_id";

        // Policy caches
        public static final String DEPRECIATION_POLICIES = "depreciation_policies";
        public static final String DEPRECIATION_POLICY_BY_ID = "depreciation_policy_by_id";

        // Billing / configuration caches
        public static final String BILLING_PLANS = "billing_plans";
        public static final String SSO_CONFIG_BY_ORG = "sso_config_by_org";
    }
}
