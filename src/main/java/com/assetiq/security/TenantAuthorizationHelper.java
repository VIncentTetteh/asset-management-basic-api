package com.assetiq.security;

import com.assetiq.multitenancy.TenantContext;
import com.assetiq.models.Organisation;

/**
 * Helper class for validating that entities belong to the current tenant.
 * Use in all controller methods to enforce organization isolation.
 */
public class TenantAuthorizationHelper {

    /**
     * Validate that an entity belongs to the current tenant context.
     * Throws SecurityException if validation fails.
     */
    public static <T extends HasOrganisation> void validateTenantAccess(T entity) {
        if (!TenantContext.hasOrganisationId()) {
            throw new SecurityException("[AUTH] No tenant context set");
        }

        if (entity == null) {
            throw new SecurityException("[AUTH] Entity is null");
        }

        Organisation org = entity.getOrganisation();
        if (org == null) {
            throw new SecurityException("[AUTH] Entity has no organisation");
        }

        if (!org.getId().equals(TenantContext.getOrganisationId())) {
            throw new SecurityException(
                "[AUTH] Entity does not belong to current tenant. " +
                "Entity org: " + org.getId() + ", Current org: " + TenantContext.getOrganisationId());
        }
    }

    /**
     * Interface for entities that belong to an organization.
     */
    public interface HasOrganisation {
        Organisation getOrganisation();
    }
}

