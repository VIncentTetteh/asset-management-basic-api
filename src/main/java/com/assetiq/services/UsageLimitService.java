package com.assetiq.services;

import com.assetiq.models.Organisation;

public interface UsageLimitService {
    void assertCanCreateAsset(Organisation organisation);

    void assertCanCreateEmployee(Organisation organisation);

    void assertAdvancedAnalyticsAccess(Organisation organisation);
}

