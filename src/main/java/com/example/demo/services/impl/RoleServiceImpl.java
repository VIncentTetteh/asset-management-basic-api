package com.example.demo.services.impl;

import com.example.demo.dto.RoleDto;
import com.example.demo.models.Role;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final OrganisationRepository organisationRepository;

    public RoleServiceImpl(RoleRepository roleRepository, OrganisationRepository organisationRepository) {
        this.roleRepository = roleRepository;
        this.organisationRepository = organisationRepository;
    }

    @Override
    public RoleDto createRole(RoleDto roleDto, UUID organisationId) {
        Organisation organisation = organisationRepository.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));

        Role role = new Role();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(roleDto.getPermissions());
        role.setOrganisation(organisation);

        Role savedRole = roleRepository.save(role);
        return mapToDto(savedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDto getRoleById(UUID id) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return mapToDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<RoleDto> getRolesByOrganisation(UUID organisationId) {
        return roleRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public RoleDto updateRole(UUID id, RoleDto roleDto) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(roleDto.getPermissions());

        Role updatedRole = roleRepository.save(role);
        return mapToDto(updatedRole);
    }

    @Override
    public void deleteRole(UUID id) {
        roleRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDto getRoleByNameAndOrganisation(String name, UUID organisationId) {
        Role role = roleRepository.findByNameAndOrganisationId(name, organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return mapToDto(role);
    }

    private RoleDto mapToDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setPermissions(role.getPermissions());
        dto.setOrganisationId(role.getOrganisation().getId());
        return dto;
    }
}

