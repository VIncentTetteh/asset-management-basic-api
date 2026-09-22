package com.assetiq.services.impl;

import com.assetiq.dto.RoleDto;
import com.assetiq.config.CachingConfig;
import com.assetiq.models.Role;
import com.assetiq.models.RolePermission;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.security.PermissionGrantGuard;
import com.assetiq.security.RbacAuditService;
import com.assetiq.security.RolePermissionDefaults;
import com.assetiq.services.RoleService;
import com.assetiq.services.TenantAwareService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl extends TenantAwareService implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionCacheService permissionCacheService;
    private final RbacAuditService rbacAuditService;

    public RoleServiceImpl(RoleRepository roleRepository,
            OrganisationRepository organisationRepository,
            PermissionCacheService permissionCacheService,
            RbacAuditService rbacAuditService) {
        super(organisationRepository);
        this.roleRepository       = roleRepository;
        this.permissionCacheService = permissionCacheService;
        this.rbacAuditService     = rbacAuditService;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.ROLES, allEntries = true)
    public RoleDto createRole(RoleDto roleDto, UUID organisationId) {
        Organisation org = requireTenantOrg();

        boolean grantAll = Boolean.TRUE.equals(roleDto.getGrantAllPermissions());
        String name = validName(roleDto.getName(), org, null, grantAll);
        List<String> requested = validPermissions(roleDto.getPermissions());
        // A caller can only create a role within their own permissions; grant-all
        // therefore needs an organisation admin.
        PermissionGrantGuard.assertCallerHolds(grantAll ? RolePermissionDefaults.allPermissionNames() : requested,
                "create this role");

        Role role = new Role();
        role.setName(name);
        role.setDescription(roleDto.getDescription());
        role.setGrantAllPermissions(grantAll);
        role.setOrganisation(org);

        // Build and attach permission entities — cascade saves them with the role.
        role.getRolePermissions().addAll(
                buildPermissions(role, requested, grantAll));

        RoleDto saved = mapToDto(roleRepository.save(role));
        // Security administration fails closed if its structured audit cannot persist.
        rbacAuditService.recordRoleCreated(saved.getId(), saved.getName());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.ROLES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':one:' + #id.toString()")
    public RoleDto getRoleById(UUID id) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return mapToDto(role);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.ROLES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':list'")
    public Set<RoleDto> getRolesByOrganisation(UUID organisationId) {
        Organisation org = requireTenantOrg();
        return roleRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.ROLES, allEntries = true)
    public RoleDto updateRole(UUID id, RoleDto roleDto) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // B-2: system roles are immutable — guard before any mutation
        if (role.isSystemRole()) {
            throw new AccessDeniedException("System roles cannot be modified");
        }

        // Snapshot before-state for the audit record
        String oldName = role.getName();
        List<String> oldPerms = role.getRolePermissions().stream()
                .map(RolePermission::getPermission).sorted().collect(Collectors.toList());

        String name = validName(roleDto.getName(), org, role.getId(), role.isGrantAllPermissions());
        List<String> requested = validPermissions(roleDto.getPermissions());
        PermissionGrantGuard.assertCallerHolds(requested, "give this role those permissions");

        role.setName(name);
        role.setDescription(roleDto.getDescription());

        // Replace the permission set wholesale; orphanRemoval deletes the old rows.
        role.getRolePermissions().clear();
        role.getRolePermissions().addAll(
                buildPermissions(role, requested, role.isGrantAllPermissions()));

        RoleDto saved = mapToDto(roleRepository.save(role));
        // B-3: targeted eviction — only this role's cache entry
        permissionCacheService.evictForRole(id);

        // P4-B: structured audit events
        if (!oldName.equals(saved.getName())) {
            rbacAuditService.recordRoleUpdated(id, oldName, saved.getName());
        }
        rbacAuditService.recordRolePermissionsChanged(id, oldPerms, saved.getPermissions());

        return saved;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.ROLES, allEntries = true)
    public RoleDto patchRole(UUID id, RoleDto roleDto) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // B-2: system roles are immutable
        if (role.isSystemRole()) {
            throw new AccessDeniedException("System roles cannot be modified");
        }

        String oldName = role.getName();

        if (roleDto.getName() != null) {
            role.setName(validName(roleDto.getName(), org, role.getId(), role.isGrantAllPermissions()));
        }
        if (roleDto.getDescription() != null) {
            role.setDescription(roleDto.getDescription());
        }

        // Only touch permissions when the patch explicitly includes them.
        List<String> oldPerms = null;
        if (roleDto.getPermissions() != null) {
            oldPerms = role.getRolePermissions().stream()
                    .map(RolePermission::getPermission).sorted().collect(Collectors.toList());
            List<String> requested = validPermissions(roleDto.getPermissions());
            PermissionGrantGuard.assertCallerHolds(requested, "give this role those permissions");
            role.getRolePermissions().clear();
            role.getRolePermissions().addAll(
                    buildPermissions(role, requested, role.isGrantAllPermissions()));
            // B-3: targeted eviction after permission change
            permissionCacheService.evictForRole(id);
        }

        RoleDto saved = mapToDto(roleRepository.save(role));

        // P4-B: structured audit events
        if (!oldName.equals(saved.getName())) {
            rbacAuditService.recordRoleUpdated(id, oldName, saved.getName());
        }
        if (oldPerms != null) {
            rbacAuditService.recordRolePermissionsChanged(id, oldPerms, saved.getPermissions());
        }

        return saved;
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.ROLES, allEntries = true)
    public void deleteRole(UUID id) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // B-2: system roles cannot be deleted
        if (role.isSystemRole()) {
            throw new AccessDeniedException("System roles cannot be deleted");
        }

        if (roleRepository.isAssignedToAnyUser(role.getId())) {
            // A deleted role still resolves its permissions for users who hold it.
            throw new IllegalStateException("Role '" + role.getName()
                    + "' is still assigned to users; move them to another role first");
        }

        String roleName = role.getName();
        role.setDeletedAt(Instant.now());
        roleRepository.save(role);

        // P4-B: structured audit event
        rbacAuditService.recordRoleDeleted(id, roleName);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CachingConfig.CacheNames.ROLES, key = "T(com.assetiq.multitenancy.TenantContext).getOrganisationId().toString() + ':name:' + #name")
    public RoleDto getRoleByNameAndOrganisation(String name, UUID organisationId) {
        Organisation org = requireTenantOrg();
        Role role = roleRepository.findByNameAndOrganisationAndDeletedAtIsNull(name, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return mapToDto(role);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Trimmed, unique within the organisation, and — unless the role is grant-all
     * anyway — not an admin-pattern name ("ADMIN", "*_ADMIN"): AdminRolePermissionBackfill turns any such role into
     * a grant-all role at the next start-up, so a custom "HR_ADMIN" would have
     * silently become a full administrator.
     */
    @SuppressWarnings("removal")
    private String validName(String requested, Organisation org, java.util.UUID selfId, boolean grantAll) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Role name is required");
        }
        String name = requested.trim();
        if (!grantAll && RolePermissionDefaults.isAdminRoleName(name)) {
            throw new IllegalArgumentException("'" + name
                    + "' is reserved for administrator roles; choose a name that does not end in _ADMIN");
        }
        roleRepository.findByNameAndOrganisationAndDeletedAtIsNull(name, org)
                .filter(other -> selfId == null || !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new IllegalStateException("A role named '" + name + "' already exists");
                });
        return name;
    }

    /** Known permission names only; a typo used to be stored and silently grant nothing. */
    private static List<String> validPermissions(List<String> requested) {
        if (requested == null) return List.of();
        Set<String> known = RolePermissionDefaults.allPermissionNames();
        List<String> cleaned = requested.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<String> unknown = cleaned.stream().filter(p -> !known.contains(p)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown permission(s): " + String.join(", ", unknown));
        }
        return cleaned;
    }

    /**
     * Converts the incoming permission name list into a set of {@link RolePermission}
     * entities ready to be cascaded alongside the owning {@link Role}.
     *
     * <p>When {@code grantAll} is {@code true} the full {@link com.assetiq.enums.Permission}
     * enum is used regardless of the requested list, maintaining the B-2 grant-all flag
     * semantics introduced in Phase 1.
     */
    private static Set<RolePermission> buildPermissions(Role role,
                                                         List<String> requested,
                                                         boolean grantAll) {
        Set<String> names;
        if (grantAll) {
            names = RolePermissionDefaults.allPermissionNames();
        } else if (requested != null && !requested.isEmpty()) {
            names = requested.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }

        return names.stream()
                .map(name -> {
                    RolePermission rp = new RolePermission();
                    rp.setRole(role);
                    rp.setPermission(name.trim());
                    return rp;
                })
                .collect(Collectors.toSet());
    }

    private RoleDto mapToDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setPermissions(
                role.getRolePermissions().stream()
                        .map(RolePermission::getPermission)
                        .sorted()
                        .collect(Collectors.toList()));
        dto.setOrganisationId(role.getOrganisation().getId());
        dto.setSystemRole(role.isSystemRole());
        dto.setGrantAllPermissions(role.isGrantAllPermissions());
        return dto;
    }
}
