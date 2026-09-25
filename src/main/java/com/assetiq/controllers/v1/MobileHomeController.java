package com.assetiq.controllers.v1;

import com.assetiq.dto.mobile.MobileHomeResponse;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.MobileHomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Screen-shaped endpoints for the mobile app. Each one replaces a fan-out of
 * list calls the phone would otherwise make and reduce itself.
 */
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileHomeController {

    private final MobileHomeService mobileHomeService;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    public MobileHomeController(MobileHomeService mobileHomeService,
                                UserRepository userRepository,
                                OrganisationRepository organisationRepository) {
        this.mobileHomeService = mobileHomeService;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
    }

    /**
     * GET /api/v1/mobile/home
     * Counts and short lists for the Home screen. Sections the caller may not
     * read are null in the response rather than a 403 for the whole screen.
     */
    @GetMapping("/home")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN','VIEW_ASSETS')")
    public ResponseEntity<MobileHomeResponse> getHome() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Organisation org = requireOrg();
        return ResponseEntity.ok(mobileHomeService.getHome(org, requireUser(auth, org), auth));
    }

    private User requireUser(Authentication auth, Organisation org) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                .orElseThrow(() -> new AccessDeniedException("User not found in organisation"));
    }

    private Organisation requireOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required. Provide X-Organisation-Id header.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException("Organisation not found"));
    }
}
