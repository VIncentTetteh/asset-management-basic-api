package com.example.demo.services;

import com.example.demo.dto.DepreciationPolicyDto;
import java.util.Set;
import java.util.UUID;

public interface DepreciationPolicyService {
    DepreciationPolicyDto createPolicy(DepreciationPolicyDto policyDto, UUID organisationId);
    DepreciationPolicyDto getPolicyById(UUID id);
    Set<DepreciationPolicyDto> getPoliciesByOrganisation(UUID organisationId);
    DepreciationPolicyDto updatePolicy(UUID id, DepreciationPolicyDto policyDto);
    DepreciationPolicyDto patchPolicy(UUID id, DepreciationPolicyDto policyDto);
    void deletePolicy(UUID id);
}
