package com.assetiq.config;

import com.assetiq.models.Role;
import com.assetiq.repositories.RoleRepository;
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

/**
 * Unit tests for {@link AdminRolePermissionBackfill}.
 *
 * Phase 2 / B-1: the backfill no longer touches the (removed) JSON permissions column.
 * It now only ensures the {@code grantAllPermissions} flag is set on admin-named roles.
 */
@ExtendWith(MockitoExtension.class)
class AdminRolePermissionBackfillTest {

    @Mock
    private RoleRepository roleRepository;

    @Test
    void run_setsGrantAllFlagOnActiveAdminRolesLackingIt() throws Exception {
        // ADMIN role that is missing the flag — should be patched.
        Role adminNeedsFlag = new Role();
        adminNeedsFlag.setName("ADMIN");
        // grantAllPermissions defaults to false

        // ORG_ADMIN that already has the flag — should be skipped.
        Role orgAdminAlreadySet = new Role();
        orgAdminAlreadySet.setName("ORG_ADMIN");
        orgAdminAlreadySet.setGrantAllPermissions(true);

        // Non-admin role — should never be touched.
        Role viewerRole = new Role();
        viewerRole.setName("VIEWER");

        // Deleted admin role — should be skipped even though the flag is missing.
        Role deletedAdmin = new Role();
        deletedAdmin.setName("SUPER_ADMIN");
        deletedAdmin.setDeletedAt(Instant.now());

        when(roleRepository.findAll()).thenReturn(List.of(
                adminNeedsFlag, orgAdminAlreadySet, viewerRole, deletedAdmin));

        new AdminRolePermissionBackfill(roleRepository)
                .run(new DefaultApplicationArguments(new String[0]));

        // Only the active admin role missing the flag should have been saved.
        verify(roleRepository).save(adminNeedsFlag);
        verify(roleRepository, never()).save(orgAdminAlreadySet);
        verify(roleRepository, never()).save(viewerRole);
        verify(roleRepository, never()).save(deletedAdmin);
    }
}
