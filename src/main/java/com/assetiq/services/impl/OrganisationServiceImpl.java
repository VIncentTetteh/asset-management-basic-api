package com.assetiq.services.impl;

import com.assetiq.dto.OrganisationDto;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.DefaultRoleSeederService;
import com.assetiq.services.OrganisationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrganisationServiceImpl implements OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final DefaultRoleSeederService defaultRoleSeederService;

    public OrganisationServiceImpl(OrganisationRepository organisationRepository,
                                   DefaultRoleSeederService defaultRoleSeederService) {
        this.organisationRepository = organisationRepository;
        this.defaultRoleSeederService = defaultRoleSeederService;
    }

    @Override
    public OrganisationDto create(OrganisationDto dto) {

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Organisation name is required");
        }

        String name = dto.getName().trim();

        if (organisationRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
            throw new IllegalStateException("Organisation with the same name already exists");
        }

        Organisation organisation = new Organisation();
        organisation.setName(name); // always use trimmed value

        if (dto.getRegistrationNumber() != null)
            organisation.setRegistrationNumber(dto.getRegistrationNumber());
        if (dto.getTaxId() != null)
            organisation.setTaxId(dto.getTaxId());
        if (dto.getIndustry() != null)
            organisation.setIndustry(dto.getIndustry());
        if (dto.getCountry() != null)
            organisation.setCountry(dto.getCountry());
        if (dto.getAddress() != null)
            organisation.setAddress(dto.getAddress());
        if (dto.getContactEmail() != null)
            organisation.setContactEmail(dto.getContactEmail());
        if (dto.getContactPhone() != null)
            organisation.setContactPhone(dto.getContactPhone());
        if (dto.getTimezone() != null)
            organisation.setTimezone(dto.getTimezone());
        if (dto.getStatus() != null)
            organisation.setStatus(dto.getStatus());

        Organisation saved = organisationRepository.save(organisation);

        // Seed the standard set of platform roles for the new organisation so
        // admins can immediately assign roles without manual setup.
        defaultRoleSeederService.seedRolesForOrganisation(saved);

        return toDto(saved);
    }

    @Override
    public OrganisationDto get(UUID id) {
        Organisation o = organisationRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (o != null && !isAuthorized(o)) {
            return null;
        }
        return o != null ? toDto(o) : null;
    }

    @Override
    public List<OrganisationDto> list() {
        Set<Organisation> result = new HashSet<>();

        if (isRestrictedAdmin()) {
            if (TenantContext.hasOrganisationId()) {
                // Return ONLY the current organization if header present
                organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                        .ifPresent(result::add);
            } else {
                // Global view: all I created
                result.addAll(organisationRepository.findAllByCreatedByAndDeletedAtIsNull(getCurrentUserEmail()));
            }
        } else {
            // Non-restricted users: strictly scoped to their tenant
            if (TenantContext.hasOrganisationId()) {
                organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                        .ifPresent(result::add);
            }
        }

        return result.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public OrganisationDto update(UUID id, OrganisationDto dto) {
        Organisation o = organisationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Organisation not found"));

        assertCanManage(o, "update");

        if (dto.getName() != null)
            o.setName(dto.getName());
        if (dto.getRegistrationNumber() != null)
            o.setRegistrationNumber(dto.getRegistrationNumber());
        if (dto.getTaxId() != null)
            o.setTaxId(dto.getTaxId());
        if (dto.getIndustry() != null)
            o.setIndustry(dto.getIndustry());
        if (dto.getCountry() != null)
            o.setCountry(dto.getCountry());
        if (dto.getAddress() != null)
            o.setAddress(dto.getAddress());
        if (dto.getContactEmail() != null)
            o.setContactEmail(dto.getContactEmail());
        if (dto.getContactPhone() != null)
            o.setContactPhone(dto.getContactPhone());
        if (dto.getTimezone() != null)
            o.setTimezone(dto.getTimezone());
        if (dto.getStatus() != null)
            o.setStatus(dto.getStatus());

        Organisation saved = organisationRepository.save(o);
        return toDto(saved);
    }

    @Override
    public OrganisationDto patch(UUID id, OrganisationDto dto) {
        return update(id, dto);
    }

    @Override
    public void delete(UUID id) {
        Organisation o = organisationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new EntityNotFoundException("Organisation not found"));

        assertCanManage(o, "delete");

        o.setDeletedAt(Instant.now());
        organisationRepository.save(o);
    }

    private void assertCanManage(Organisation organisation, String action) {
        if (TenantContext.hasOrganisationId()) {
            if (organisation.getId().equals(TenantContext.getOrganisationId())) {
                return;
            }
            throw new AccessDeniedException("You do not have permission to " + action + " this organisation");
        }

        String creator = organisation.getCreatedBy();
        if (isRestrictedAdmin() && creator != null && creator.equals(getCurrentUserEmail())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to " + action + " this organisation");
    }

    private boolean isRestrictedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAuthorized(Organisation o) {
        if (!isRestrictedAdmin())
            return true;
        if (o == null)
            return false;

        String creator = o.getCreatedBy();
        if (creator != null && creator.equals(getCurrentUserEmail()))
            return true;

        if (TenantContext.hasOrganisationId() && o.getId().equals(TenantContext.getOrganisationId()))
            return true;

        return false;
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private OrganisationDto toDto(Organisation o) {
        OrganisationDto dto = new OrganisationDto();
        dto.setId(o.getId());
        dto.setName(o.getName());
        dto.setRegistrationNumber(o.getRegistrationNumber());
        dto.setTaxId(o.getTaxId());
        dto.setIndustry(o.getIndustry());
        dto.setCountry(o.getCountry());
        dto.setAddress(o.getAddress());
        dto.setContactEmail(o.getContactEmail());
        dto.setContactPhone(o.getContactPhone());
        dto.setTimezone(o.getTimezone());
        dto.setStatus(o.getStatus());
        return dto;
    }
}
