package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import org.springframework.security.access.AccessDeniedException;

/**
 * Base class for services that require a resolved tenant context.
 * Subclasses call {@link #requireTenantOrg()} to get the current org
 * and throw 403 if no tenant is set.
 */
public abstract class TenantAwareService {

    protected final OrganisationRepository organisationRepository;

    protected TenantAwareService(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    /**
     * Returns the Organisation for the current tenant context.
     * Throws AccessDeniedException (mapped to HTTP 403) if no tenant is set or org
     * not found.
     */
    protected Organisation requireTenantOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required. Provide a valid X-Organisation-Id header.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Organisation not found or inactive for current tenant context."));
    }
}
