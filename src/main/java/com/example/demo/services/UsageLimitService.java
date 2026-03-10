package com.example.demo.services;

import com.example.demo.models.Organisation;

public interface UsageLimitService {
    void assertCanCreateAsset(Organisation organisation);

    void assertCanCreateEmployee(Organisation organisation);

    void assertAdvancedAnalyticsAccess(Organisation organisation);
}

