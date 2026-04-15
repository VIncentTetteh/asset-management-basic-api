package com.assetiq.services.impl;

import com.assetiq.dto.RoleDto;
import com.assetiq.models.Role;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.RolePermissionDefaults;
import com.assetiq.services.RoleService;
import com.assetiq.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl extends TenantAwareService implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionCacheService permissionCacheService;

    public RoleServiceImpl(RoleRepository roleRepository,
            OrganisationRepository organisationRepository,
            PermissionCacheService permissionCacheService) {
        super(organisationRepository);
        this.roleRepository = roleRepository;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public RoleDto createRole(RoleDto roleDto, UUID organisationId) {
        // Always use tenant context, ignore param
        Organisation org = requireTenantOrg();

        Role role = new Role();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(RolePermissionDefaults.defaultPermissionsForRole(roleDto.getName(), roleDto.getPermissions()));
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

        String previousPermissions = role.getPermissions();
        role.setName(roleDto.getName());
        role.setDescription(roleDto.getDescription());
        role.setPermissions(RolePermissionDefaults.defaultPermissionsForRole(roleDto.getName(), roleDto.getPermissions()));

        RoleDto saved = mapToDto(roleRepository.save(role));
        // Evict cached permissions for every user with this role so changes
        // take effect immediately without requiring the user to re-login.
        if (!Objects.equals(previousPermissions, role.getPermissions())) {
            permissionCacheService.evictForRole(id);
        }
        return saved;
    }

    @Override
    public RoleDto patchRole(UUID id, RoleDto roleDto) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        String previousPermissions = role.getPermissions();
        String effectiveRoleName = roleDto.getName() != null ? roleDto.getName() : role.getName();

        if (roleDto.getName() != null) {
            role.setName(roleDto.getName());
        }
        if (roleDto.getDescription() != null) {
            role.setDescription(roleDto.getDescription());
        }

        if (RolePermissionDefaults.isAdminRoleName(effectiveRoleName)) {
            role.setPermissions(RolePermissionDefaults.allPermissionsJson());
        } else if (roleDto.getPermissions() != null) {
            role.setPermissions(roleDto.getPermissions());
        }

        RoleDto patched = mapToDto(roleRepository.save(role));
        if (!Objects.equals(previousPermissions, role.getPermissions())) {
            permissionCacheService.evictForRole(id);
        }
        return patched;
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
