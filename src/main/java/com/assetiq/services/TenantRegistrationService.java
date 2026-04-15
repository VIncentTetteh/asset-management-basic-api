package com.assetiq.services;

import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;

public interface TenantRegistrationService {
    TenantRegisterResponse registerTenant(TenantRegisterRequest request);
}
