package com.assetiq.controllers.v1;

import com.assetiq.dto.OrganisationDto;
import com.assetiq.security.PlatformAdminGuard;
import com.assetiq.services.OrganisationService;
import com.assetiq.validation.OnCreate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;
    private final PlatformAdminGuard platformAdminGuard;

    public OrganisationController(OrganisationService organisationService,
                                  PlatformAdminGuard platformAdminGuard) {
        this.organisationService = organisationService;
        this.platformAdminGuard = platformAdminGuard;
    }

    /**
     * Creates a tenant. Operators only: any tenant admin used to be able to POST
     * here and stand up an organisation nobody was billing for. Customers sign
     * themselves up through /tenant/register, which goes through the plan and
     * the billing record. Invisible (404) to everyone else, like the other
     * platform operations.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganisationDto> create(@Validated(OnCreate.class) @RequestBody OrganisationDto dto) {
        if (!platformAdminGuard.isPlatformAdmin()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(organisationService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<OrganisationDto> get(@PathVariable UUID id) {
        OrganisationDto dto = organisationService.get(id);
        if (dto == null) throw new com.assetiq.exceptions.ResourceNotFoundException("Organisation not found");
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_ASSETS')")
    public ResponseEntity<List<OrganisationDto>> list() {
        return ResponseEntity.ok(organisationService.list());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<OrganisationDto> update(@PathVariable UUID id, @Validated(OnCreate.class) @RequestBody OrganisationDto dto) {
        return ResponseEntity.ok(organisationService.update(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<OrganisationDto> patch(@PathVariable UUID id, @Valid @RequestBody OrganisationDto dto) {
        return ResponseEntity.ok(organisationService.patch(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        organisationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
