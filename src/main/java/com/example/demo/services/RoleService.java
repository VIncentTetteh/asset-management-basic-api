package com.example.demo.services;

import com.example.demo.dto.RoleDto;
import java.util.Set;
import java.util.UUID;

public interface RoleService {
    RoleDto createRole(RoleDto roleDto, UUID organisationId);
    RoleDto getRoleById(UUID id);
    Set<RoleDto> getRolesByOrganisation(UUID organisationId);
    RoleDto updateRole(UUID id, RoleDto roleDto);
    void deleteRole(UUID id);
    RoleDto getRoleByNameAndOrganisation(String name, UUID organisationId);
}

