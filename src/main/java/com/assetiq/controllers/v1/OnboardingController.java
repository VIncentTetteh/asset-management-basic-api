package com.assetiq.controllers.v1;

import com.assetiq.dto.onboarding.OnboardingStatusDto;
import com.assetiq.services.TenantOnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first-run checklist for a newly registered company.
 *
 * <p>Readable by anyone signed in — it says nothing they cannot already see —
 * and dismissible only by someone who can change organisation settings, because
 * hiding it hides it for the whole company.
 *
 * <p>Distinct from employee onboarding under {@code /api/v1/employees}, which is
 * a joiner's asset-handover checklist.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final TenantOnboardingService onboardingService;

    public OnboardingController(TenantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /** GET /api/v1/onboarding — what this tenant still needs, derived from live counts. */
    @GetMapping
    public ResponseEntity<OnboardingStatusDto> status(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.status(name(authentication)));
    }

    /** POST /api/v1/onboarding/dismiss — hide the prompt. Steps keep reporting the truth. */
    @PostMapping("/dismiss")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_USERS')")
    public ResponseEntity<OnboardingStatusDto> dismiss(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.dismiss(name(authentication)));
    }

    /** DELETE /api/v1/onboarding/dismiss — show it again. */
    @DeleteMapping("/dismiss")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS','MANAGE_USERS')")
    public ResponseEntity<OnboardingStatusDto> restore(Authentication authentication) {
        return ResponseEntity.ok(onboardingService.restore(name(authentication)));
    }

    private static String name(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
