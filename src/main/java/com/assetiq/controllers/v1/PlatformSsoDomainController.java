package com.assetiq.controllers.v1;

import com.assetiq.dto.SsoDomainStatusDto;
import com.assetiq.security.PlatformAdminGuard;
import com.assetiq.services.SsoConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Approving a tenant's SSO email domain by hand, for deployments where the server
 * cannot make DNS lookups. Invisible (404) to everyone but platform operators —
 * a tenant approving its own claim would defeat the point of verifying it.
 */
@RestController
@RequestMapping("/api/v1/platform/sso-domains")
public class PlatformSsoDomainController {

    private final PlatformAdminGuard platformAdminGuard;
    private final SsoConfigService ssoConfigService;

    public PlatformSsoDomainController(PlatformAdminGuard platformAdminGuard,
                                       SsoConfigService ssoConfigService) {
        this.platformAdminGuard = platformAdminGuard;
        this.ssoConfigService = ssoConfigService;
    }

    @PostMapping("/{organisationId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SsoDomainStatusDto> approve(@PathVariable UUID organisationId) {
        if (!platformAdminGuard.isPlatformAdmin()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ssoConfigService.approveDomain(organisationId));
    }
}
