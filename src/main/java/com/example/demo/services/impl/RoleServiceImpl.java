package com.example.demo.services.impl;

import com.example.demo.dto.RoleDto;
import com.example.demo.models.Role;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.RoleService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl extends TenantAwareService implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository,
            OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.roleRepository = roleRepository;
    }

    @Override
    public RoleDto createRole(RoleDto roleDto, UUID organisationId) {
        // Always use tenant context, ignore param
        Organisation org = requireTenantOrg();

        Role role = new Role();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(roleDto.getPermissions());
        role.setOrganisation(org);

        return mapToDto(roleRepository.save(role));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDto getRoleById(UUID id) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return mapToDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<RoleDto> getRolesByOrganisation(UUID organisationId) {
        // Always scope to tenant context, ignore param
        Organisation org = requireTenantOrg();
        return roleRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public RoleDto updateRole(UUID id, RoleDto roleDto) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(roleDto.getPermissions());

        return mapToDto(roleRepository.save(role));
    }

    @Override
    public RoleDto patchRole(UUID id, RoleDto roleDto) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (roleDto.getName() != null) {
            role.setName(roleDto.getName());
        }
        if (roleDto.getDescription() != null) {
            role.setDescription(roleDto.getDescription());
        }
        if (roleDto.getPermissions() != null) {
            role.setPermissions(roleDto.getPermissions());
        }

        return mapToDto(roleRepository.save(role));
    }

    @Override
    public void deleteRole(UUID id) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        role.setDeletedAt(Instant.now());
        roleRepository.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDto getRoleByNameAndOrganisation(String name, UUID organisationId) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByNameAndOrganisationAndDeletedAtIsNull(name, org)
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
