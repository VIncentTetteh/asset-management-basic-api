package com.example.demo.config;

import com.example.demo.models.Role;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.security.RolePermissionDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRolePermissionBackfillTest {

    @Mock
    private RoleRepository roleRepository;

    @Test
    void run_updatesOnlyActiveAdminRolesWithoutFullPermissions() throws Exception {
        Role adminRoleNeedingUpdate = new Role();
        adminRoleNeedingUpdate.setName("ADMIN");
        adminRoleNeedingUpdate.setPermissions("[\"VIEW_ASSETS\"]");

        Role orgAdminAlreadyFull = new Role();
        orgAdminAlreadyFull.setName("ORG_ADMIN");
        orgAdminAlreadyFull.setPermissions(RolePermissionDefaults.allPermissionsJson());

        Role nonAdminRole = new Role();
        nonAdminRole.setName("VIEWER");
        nonAdminRole.setPermissions("[\"VIEW_ASSETS\"]");

        Role deletedAdminRole = new Role();
        deletedAdminRole.setName("SUPER_ADMIN");
        deletedAdminRole.setPermissions("[\"VIEW_ASSETS\"]");
        deletedAdminRole.setDeletedAt(Instant.now());

        when(roleRepository.findAll()).thenReturn(List.of(
                adminRoleNeedingUpdate,
                orgAdminAlreadyFull,
                nonAdminRole,
                deletedAdminRole));

        AdminRolePermissionBackfill backfill = new AdminRolePermissionBackfill(roleRepository);
        backfill.run(new DefaultApplicationArguments(new String[0]));

        verify(roleRepository).save(adminRoleNeedingUpdate);
        verify(roleRepository, never()).save(orgAdminAlreadyFull);
        verify(roleRepository, never()).save(nonAdminRole);
        verify(roleRepository, never()).save(deletedAdminRole);
    }
}
