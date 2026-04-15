package com.assetiq.security;

import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.OrganisationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Service("tenantAuthorizationService")
public class TenantAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthorizationService.class);

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    public TenantAuthorizationService(
            AssetRepository assetRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
    }

    /**
     * Check if current user's organization owns the asset.
     */
    public boolean isAssetAccessible(UUID assetId) {
        if (!TenantContext.hasOrganisationId()) {
            log.warn("[AUTH] No tenant context for asset access check");
            return false;
        }

        UUID userOrgId = TenantContext.getOrganisationId();
        var asset = assetRepository.findById(assetId);

        if (asset.isEmpty()) {
            return false;
        }

        boolean isOwned = asset.get().getOrganisation() != null &&
                asset.get().getOrganisation().getId().equals(userOrgId);

        if (!isOwned) {
            log.warn("[AUTH] Access denied: asset {} does not belong to org {}", assetId, userOrgId);
        }

        return isOwned;
    }

    /**
     * Check if current user is an admin.
     */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Check if current user has a specific role.
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        String rolePrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(rolePrefix));
    }

    /**
     * Check if current user is in the specified organization.
     */
    public boolean isInOrganization(UUID orgId) {
        if (!TenantContext.hasOrganisationId()) {
            return false;
        }
        return TenantContext.getOrganisationId().equals(orgId);
    }
}

