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
}
