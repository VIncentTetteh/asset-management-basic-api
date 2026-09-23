package com.assetiq.services;

import com.assetiq.dto.RoleDto;
import java.util.Set;
import java.util.UUID;

public interface RoleService {
    RoleDto createRole(RoleDto roleDto, UUID organisationId);
    RoleDto getRoleById(UUID id);
    Set<RoleDto> getRolesByOrganisation(UUID organisationId);
    RoleDto updateRole(UUID id, RoleDto roleDto);
    RoleDto patchRole(UUID id, RoleDto roleDto);
    void deleteRole(UUID id);
    RoleDto getRoleByNameAndOrganisation(String name, UUID organisationId);

    /**
     * What a role actually lets a person do, in words rather than authority
     * strings, so a screen can honestly answer "this person will be able to…"
     * before anyone is given the role.
     *
     * <p>Resolves grant-all roles to the full permission set, and flags any
     * permission the codebase does not in fact enforce — a role that "has" one
     * and cannot do the thing is worse than one that never offered it.
     */
    com.assetiq.dto.invitation.RoleEffectivePermissionsDto getEffectivePermissions(UUID roleId);

    /**
     * Every permission the platform defines, described the same way. The input to
     * a permission matrix; static, so it needs no tenant.
     */
    java.util.List<com.assetiq.dto.invitation.PermissionDescriptionDto> permissionCatalogue();
}
