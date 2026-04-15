package com.assetiq.services.impl;

import com.assetiq.dto.DepreciationPolicyDto;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.DepreciationPolicyRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.DepreciationPolicyService;
import com.assetiq.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepreciationPolicyServiceImpl extends TenantAwareService implements DepreciationPolicyService {

    private final DepreciationPolicyRepository policyRepository;

    public DepreciationPolicyServiceImpl(DepreciationPolicyRepository policyRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.policyRepository = policyRepository;
    }

    @Override
    public DepreciationPolicyDto createPolicy(DepreciationPolicyDto policyDto, UUID organisationId) {
        // Always use tenant context, ignore param
        Organisation org = requireTenantOrg();

        DepreciationPolicy policy = new DepreciationPolicy();
        policy.setName(policyDto.getName());
        policy.setDescription(policyDto.getDescription());
        policy.setMethod(policyDto.getMethod());
        policy.setUsefulLifeMonths(policyDto.getUsefulLifeMonths());
        policy.setSalvageValuePercent(policyDto.getSalvageValuePercent());
        policy.setOrganisation(org);

        return mapToDto(policyRepository.save(policy));
    }

    @Override
    @Transactional(readOnly = true)
    public DepreciationPolicyDto getPolicyById(UUID id) {
        Organisation org = requireTenantOrg();
        DepreciationPolicy policy = policyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        return mapToDto(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DepreciationPolicyDto> getPoliciesByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return policyRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public DepreciationPolicyDto updatePolicy(UUID id, DepreciationPolicyDto policyDto) {
        Organisation org = requireTenantOrg();
        DepreciationPolicy policy = policyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        policy.setName(policyDto.getName());
        policy.setDescription(policyDto.getDescription());
        policy.setMethod(policyDto.getMethod());
        policy.setUsefulLifeMonths(policyDto.getUsefulLifeMonths());
        policy.setSalvageValuePercent(policyDto.getSalvageValuePercent());

        return mapToDto(policyRepository.save(policy));
    }

    @Override
    public DepreciationPolicyDto patchPolicy(UUID id, DepreciationPolicyDto policyDto) {
        Organisation org = requireTenantOrg();
        DepreciationPolicy policy = policyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        if (policyDto.getName() != null) {
            policy.setName(policyDto.getName());
        }
        if (policyDto.getDescription() != null) {
            policy.setDescription(policyDto.getDescription());
        }
        if (policyDto.getMethod() != null) {
            policy.setMethod(policyDto.getMethod());
        }
        if (policyDto.getUsefulLifeMonths() != null) {
            policy.setUsefulLifeMonths(policyDto.getUsefulLifeMonths());
        }
        if (policyDto.getSalvageValuePercent() != null) {
            policy.setSalvageValuePercent(policyDto.getSalvageValuePercent());
        }

        return mapToDto(policyRepository.save(policy));
    }

    @Override
    public void deletePolicy(UUID id) {
        Organisation org = requireTenantOrg();
        DepreciationPolicy policy = policyRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        policy.setDeletedAt(Instant.now());
        policyRepository.save(policy);
    }

    private DepreciationPolicyDto mapToDto(DepreciationPolicy policy) {
        DepreciationPolicyDto dto = new DepreciationPolicyDto();
        dto.setId(policy.getId());
        dto.setName(policy.getName());
        dto.setDescription(policy.getDescription());
        dto.setMethod(policy.getMethod());
        dto.setUsefulLifeMonths(policy.getUsefulLifeMonths());
        dto.setSalvageValuePercent(policy.getSalvageValuePercent());
        dto.setOrganisationId(policy.getOrganisation().getId());
        return dto;
    }
}
