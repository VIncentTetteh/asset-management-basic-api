package com.assetiq.controllers.v1;

import com.assetiq.dto.RoleDto;
import com.assetiq.enums.Permission;
import com.assetiq.security.annotation.EnforceTenant;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.services.RoleService;
import com.assetiq.validation.OnCreate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    @EnforceTenant
    public ResponseEntity<RoleDto> createRole(@Validated(OnCreate.class) @RequestBody RoleDto roleDto,
                                              @RequestParam UUID organisationId) {
        RoleDto createdRole = roleService.createRole(roleDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRole);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    @EnforceTenant
    public ResponseEntity<Set<RoleDto>> getRolesByOrganisation(@RequestParam UUID organisationId) {
        return ResponseEntity.ok(roleService.getRolesByOrganisation(organisationId));
    }

    @PutMapping("/{id}")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> updateRole(@PathVariable UUID id,
                                              @Validated(OnCreate.class) @RequestBody RoleDto roleDto) {
        return ResponseEntity.ok(roleService.updateRole(id, roleDto));
    }

    @PatchMapping("/{id}")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<RoleDto> patchRole(@PathVariable UUID id,
                                             @Valid @RequestBody RoleDto roleDto) {
        return ResponseEntity.ok(roleService.patchRole(id, roleDto));
    }

    @DeleteMapping("/{id}")
    @RequireFreshMfa
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

    /**
     * GET /api/v1/roles/permissions/catalogue — every permission, described in
     * plain language and grouped by area of the product.
     *
     * <p>{@code /roles/permissions} above stays exactly as it was (a flat list of
     * authority strings) because the web app already consumes it; this is the
     * version a permission matrix can render without inventing its own labels,
     * and it is the only place that says which permissions gate nothing today.
     */
    @GetMapping("/permissions/catalogue")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS','VIEW_ASSETS')")
    public ResponseEntity<List<com.assetiq.dto.invitation.PermissionDescriptionDto>> getPermissionCatalogue() {
        return ResponseEntity.ok(roleService.permissionCatalogue());
    }

    /**
     * GET /api/v1/roles/{id}/effective-permissions — what someone holding this
     * role will actually be able to do, resolved (grant-all included) and worded
     * for a human.
     */
    @GetMapping("/{id}/effective-permissions")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS','MANAGE_USERS','VIEW_USERS')")
    public ResponseEntity<com.assetiq.dto.invitation.RoleEffectivePermissionsDto> getEffectivePermissions(
            @PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getEffectivePermissions(id));
    }

    @GetMapping("/by-name")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN','VIEW_ROLES','MANAGE_ROLES','MANAGE_ORGANIZATION_SETTINGS')")
    @EnforceTenant
    public ResponseEntity<RoleDto> getRoleByName(@RequestParam String name,
                                                 @RequestParam UUID organisationId) {
        return ResponseEntity.ok(roleService.getRoleByNameAndOrganisation(name, organisationId));
    }
}
