package com.example.demo.services;

import com.example.demo.dto.TenantRegisterRequest;
import com.example.demo.dto.TenantRegisterResponse;

public interface TenantRegistrationService {
    TenantRegisterResponse registerTenant(TenantRegisterRequest request);
}
