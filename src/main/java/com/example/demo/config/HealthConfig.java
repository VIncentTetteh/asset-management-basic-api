package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class HealthConfig {
    // Health-related configuration
    // AssetManagementHealthIndicator is defined in its own file
}

