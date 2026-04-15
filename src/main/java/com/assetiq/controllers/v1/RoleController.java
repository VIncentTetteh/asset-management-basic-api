package com.assetiq.controllers.v1;

import com.assetiq.dto.RoleDto;
import com.assetiq.enums.Permission;
import com.assetiq.services.RoleService;
import com.assetiq.multitenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody RoleDto roleDto,
                                             @RequestParam UUID organisationId) {
        requireSameOrganisation(organisationId);
        RoleDto createdRole = roleService.createRole(roleDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRole);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable UUID id) {
        RoleDto role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Set<RoleDto>> getRolesByOrganisation(@RequestParam UUID organisationId) {
        requireSameOrganisation(organisationId);
        Set<RoleDto> roles = roleService.getRolesByOrganisation(organisationId);
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> updateRole(@PathVariable UUID id,
                                             @Valid @RequestBody RoleDto roleDto) {
        RoleDto updatedRole = roleService.updateRole(id, roleDto);
        return ResponseEntity.ok(updatedRole);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> patchRole(@PathVariable UUID id,
            @RequestBody RoleDto roleDto) {
        RoleDto updatedRole = roleService.patchRole(id, roleDto);
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS','VIEW_ASSETS')")
    public ResponseEntity<List<String>> getAvailablePermissions() {
        List<String> permissions = Arrays.stream(Permission.values())
                .map(Permission::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/by-name")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> getRoleByName(@RequestParam String name,
                                                @RequestParam UUID organisationId) {
        requireSameOrganisation(organisationId);
        RoleDto role = roleService.getRoleByNameAndOrganisation(name, organisationId);
        return ResponseEntity.ok(role);
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
}
