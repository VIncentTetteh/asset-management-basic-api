package com.assetiq.controllers.v1;

import com.assetiq.dto.PlanGrantRequest;
import com.assetiq.security.PlatformAdminGuard;
import com.assetiq.services.impl.PlatformSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Vendor operations on tenant subscriptions. Invisible (404) to everyone but platform operators. */
@RestController
@RequestMapping("/api/v1/platform/subscriptions")
public class PlatformSubscriptionController {

    private final PlatformAdminGuard platformAdminGuard;
    private final PlatformSubscriptionService platformSubscriptionService;

    public PlatformSubscriptionController(PlatformAdminGuard platformAdminGuard,
                                          PlatformSubscriptionService platformSubscriptionService) {
        this.platformAdminGuard = platformAdminGuard;
        this.platformSubscriptionService = platformSubscriptionService;
    }

    @PostMapping("/{organisationId}/grant")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> grant(@PathVariable UUID organisationId,
                                      @Valid @RequestBody PlanGrantRequest request) {
        if (!platformAdminGuard.isPlatformAdmin()) {
            return ResponseEntity.notFound().build();
        }
        platformSubscriptionService.grant(organisationId, request);
        return ResponseEntity.noContent().build();
    }
}
