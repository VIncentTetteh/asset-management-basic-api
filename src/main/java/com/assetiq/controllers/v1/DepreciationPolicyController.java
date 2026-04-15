package com.assetiq.controllers.v1;

import com.assetiq.dto.DepreciationPolicyDto;
import com.assetiq.services.DepreciationPolicyService;
import com.assetiq.multitenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/depreciation-policies")
public class DepreciationPolicyController {

    private final DepreciationPolicyService policyService;

    public DepreciationPolicyController(DepreciationPolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPRECIATION')")
    public ResponseEntity<DepreciationPolicyDto> createPolicy(@Valid @RequestBody DepreciationPolicyDto policyDto,
                                                             @RequestParam UUID organisationId) {
        requireSameOrganisation(organisationId);
        DepreciationPolicyDto createdPolicy = policyService.createPolicy(policyDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPolicy);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_DEPRECIATION','MANAGE_DEPRECIATION','VIEW_REPORTS')")
    public ResponseEntity<DepreciationPolicyDto> getPolicyById(@PathVariable UUID id) {
        DepreciationPolicyDto policy = policyService.getPolicyById(id);
        return ResponseEntity.ok(policy);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_DEPRECIATION','MANAGE_DEPRECIATION','VIEW_REPORTS')")
    public ResponseEntity<Set<DepreciationPolicyDto>> getPoliciesByOrganisation(@RequestParam UUID organisationId) {
        requireSameOrganisation(organisationId);
        Set<DepreciationPolicyDto> policies = policyService.getPoliciesByOrganisation(organisationId);
        return ResponseEntity.ok(policies);
    }

    private static void requireSameOrganisation(UUID organisationId) {
        UUID current = TenantContext.getOrganisationId();
        if (current == null) {
            throw new AccessDeniedException("Tenant context is required");
        }
        if (organisationId == null || !organisationId.equals(current)) {
            throw new AccessDeniedException("organisationId does not match current tenant");
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPRECIATION')")
    public ResponseEntity<DepreciationPolicyDto> updatePolicy(@PathVariable UUID id,
                                                             @Valid @RequestBody DepreciationPolicyDto policyDto) {
        DepreciationPolicyDto updatedPolicy = policyService.updatePolicy(id, policyDto);
        return ResponseEntity.ok(updatedPolicy);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPRECIATION')")
    public ResponseEntity<DepreciationPolicyDto> patchPolicy(@PathVariable UUID id,
            @RequestBody DepreciationPolicyDto policyDto) {
        DepreciationPolicyDto updatedPolicy = policyService.patchPolicy(id, policyDto);
        return ResponseEntity.ok(updatedPolicy);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPRECIATION')")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
