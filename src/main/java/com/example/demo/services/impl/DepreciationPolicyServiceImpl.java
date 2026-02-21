package com.example.demo.services.impl;

import com.example.demo.dto.DepreciationPolicyDto;
import com.example.demo.models.DepreciationPolicy;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.DepreciationPolicyRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.DepreciationPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DepreciationPolicyServiceImpl implements DepreciationPolicyService {

    private final DepreciationPolicyRepository policyRepository;
    private final OrganisationRepository organisationRepository;

    public DepreciationPolicyServiceImpl(DepreciationPolicyRepository policyRepository,
                                        OrganisationRepository organisationRepository) {
        this.policyRepository = policyRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public DepreciationPolicyDto createPolicy(DepreciationPolicyDto policyDto, UUID organisationId) {
        Organisation organisation = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        DepreciationPolicy policy = new DepreciationPolicy();
        policy.setName(policyDto.getName());
        policy.setDescription(policyDto.getDescription());
        policy.setMethod(policyDto.getMethod());
        policy.setUsefulLifeMonths(policyDto.getUsefulLifeMonths());
        policy.setSalvageValuePercent(policyDto.getSalvageValuePercent());
        policy.setOrganisation(organisation);

        DepreciationPolicy savedPolicy = policyRepository.save(policy);
        return mapToDto(savedPolicy);
    }

    @Override
    @Transactional(readOnly = true)
    public DepreciationPolicyDto getPolicyById(UUID id) {
        DepreciationPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found"));
        return mapToDto(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DepreciationPolicyDto> getPoliciesByOrganisation(UUID organisationId) {
        return policyRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public DepreciationPolicyDto updatePolicy(UUID id, DepreciationPolicyDto policyDto) {
        DepreciationPolicy policy = policyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

        policy.setName(policyDto.getName());
        policy.setDescription(policyDto.getDescription());
        policy.setMethod(policyDto.getMethod());
        policy.setUsefulLifeMonths(policyDto.getUsefulLifeMonths());
        policy.setSalvageValuePercent(policyDto.getSalvageValuePercent());

        DepreciationPolicy updatedPolicy = policyRepository.save(policy);
        return mapToDto(updatedPolicy);
    }

    @Override
    public void deletePolicy(UUID id) {
        policyRepository.deleteById(id);
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

