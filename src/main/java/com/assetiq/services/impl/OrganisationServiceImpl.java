package com.assetiq.services.impl;

import com.assetiq.dto.OrganisationDto;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CurrencyResolver;
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

        // P1-1: Explicit billing currency wins; otherwise derive from country.
        if (dto.getBillingCurrency() != null && !dto.getBillingCurrency().isBlank()) {
            organisation.setBillingCurrency(CurrencyResolver.normaliseIsoCode(dto.getBillingCurrency()));
        } else {
            organisation.setBillingCurrency(CurrencyResolver.currencyForCountry(dto.getCountry()));
        }

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
        boolean ownTenant = TenantContext.hasOrganisationId() && o.getId().equals(TenantContext.getOrganisationId());
        if (ownTenant && dto.getStatus() != null && dto.getStatus() != o.getStatus()) {
            // Setting your own tenant INACTIVE/SUSPENDED signs everyone out for good
            // (the JWT filter refuses inactive tenants) with nobody left to undo it.
            // Suspension is billing's job; closure goes through account deletion.
            throw new IllegalStateException("Your organisation's status is managed by billing and account closure, "
                    + "not by this form");
        }

        if (dto.getName() != null && !dto.getName().isBlank()) {
            String name = dto.getName().trim();
            if (!name.equalsIgnoreCase(o.getName())
                    && organisationRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
                throw new IllegalStateException("Organisation with the same name already exists");
            }
            o.setName(name);
        }
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
        if (dto.getBillingCurrency() != null && !dto.getBillingCurrency().isBlank())
            o.setBillingCurrency(CurrencyResolver.normaliseIsoCode(dto.getBillingCurrency()));

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
        if (TenantContext.hasOrganisationId() && o.getId().equals(TenantContext.getOrganisationId())) {
            // Soft-deleting the tenant you are signed into skipped the account-closure
            // flow (confirmation, export window, purge schedule) entirely.
            throw new IllegalStateException("Close your own organisation through account closure, not this endpoint");
        }

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

    /**
     * Readable when it is the caller's tenant, or (platform admins) one they
     * created. Non-admins used to be allowed to read ANY organisation by id,
     * leaking other tenants' contact and tax details.
     */
    private boolean isAuthorized(Organisation o) {
        if (o == null)
            return false;
        if (!isRestrictedAdmin())
            return TenantContext.hasOrganisationId() && o.getId().equals(TenantContext.getOrganisationId());

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
        dto.setBillingCurrency(o.getBillingCurrency());
        return dto;
    }
}
