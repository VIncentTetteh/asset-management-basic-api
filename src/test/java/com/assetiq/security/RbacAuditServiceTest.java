package com.assetiq.security;

import com.assetiq.enums.AuditEventType;
import com.assetiq.models.AuditEvent;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AuditEventRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RbacAuditService}.
 *
 * Verifies:
 *  1. recordRolePermissionsChanged persists an AuditEvent with the correct type and delta.
 *  2. recordUserRoleAssigned persists an AuditEvent with oldValue / newValue.
 *  3. recordPermissionDenied persists an AuditEvent typed PERMISSION_DENIED.
 *  4. Actor is resolved from the security context.
 *  5. Failures during save are swallowed (audit is best-effort).
 */
@ExtendWith(MockitoExtension.class)
class RbacAuditServiceTest {

    @Mock private AuditEventRepository  auditEventRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private UserRepository         userRepository;

    private RbacAuditService rbacAuditService;

    private final UUID   orgId = UUID.randomUUID();
    private final UUID   userId = UUID.randomUUID();
    private final String actorEmail = "admin@acme.com";

    @BeforeEach
    void setUp() {
        rbacAuditService = new RbacAuditService(
                auditEventRepository, organisationRepository, userRepository);

        // Simulate a logged-in admin
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorEmail, null, List.of()));

        // Tenant context
        TenantContext.setOrganisationId(orgId);

        // Organisation lookup
        Organisation org = new Organisation();
        org.setId(orgId);
        when(organisationRepository.findByIdAndDeletedAtIsNull(orgId)).thenReturn(Optional.of(org));

        // Actor lookup
        User actor = new User();
        actor.setId(userId);
        actor.setEmail(actorEmail);
        when(userRepository.findByEmailAndOrganisationId(actorEmail, orgId))
                .thenReturn(Optional.of(actor));

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── Role permission change ────────────────────────────────────────────────

    @Test
    @DisplayName("recordRolePermissionsChanged persists event with ROLE_PERMISSIONS_CHANGED type")
    void rolePermissionsChanged_persistsCorrectEventType() {
        UUID roleId = UUID.randomUUID();

        rbacAuditService.recordRolePermissionsChanged(
                roleId,
                List.of("VIEW_ASSETS", "CREATE_ASSET"),
                List.of("VIEW_ASSETS", "CREATE_ASSET", "EDIT_ASSET"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.ROLE_PERMISSIONS_CHANGED);
        assertThat(event.getTargetId()).isEqualTo(roleId.toString());
        assertThat(event.getOldValue()).contains("CREATE_ASSET");
        assertThat(event.getNewValue()).contains("EDIT_ASSET");
        assertThat(event.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("recordRolePermissionsChanged sorts permission names in old/new values")
    void rolePermissionsChanged_sortedDelta() {
        UUID roleId = UUID.randomUUID();

        rbacAuditService.recordRolePermissionsChanged(
                roleId,
                List.of("EDIT_ASSET", "CREATE_ASSET"),    // unsorted
                List.of("VIEW_ASSETS", "CREATE_ASSET"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        // Sorted: CREATE_ASSET comes before EDIT_ASSET
        assertThat(event.getOldValue()).startsWith("CREATE_ASSET");
    }

    // ── User role assignment ──────────────────────────────────────────────────

    @Test
    @DisplayName("recordUserRoleAssigned persists event with USER_ROLE_ASSIGNED type")
    void userRoleAssigned_persistsCorrectEventType() {
        UUID targetUserId = UUID.randomUUID();

        rbacAuditService.recordUserRoleAssigned(targetUserId, "VIEWER", "MANAGER");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.USER_ROLE_ASSIGNED);
        assertThat(event.getTargetId()).isEqualTo(targetUserId.toString());
        assertThat(event.getOldValue()).isEqualTo("VIEWER");
        assertThat(event.getNewValue()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("recordUserRoleAssigned handles null oldRoleName (first assignment)")
    void userRoleAssigned_nullOldRole() {
        rbacAuditService.recordUserRoleAssigned(UUID.randomUUID(), null, "ADMIN");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getOldValue()).isNull();
        assertThat(event.getNewValue()).isEqualTo("ADMIN");
    }

    // ── Permission denied ─────────────────────────────────────────────────────

    @Test
    @DisplayName("recordPermissionDenied persists event with PERMISSION_DENIED type and 403 status")
    void permissionDenied_persistsCorrectEvent() {
        rbacAuditService.recordPermissionDenied(actorEmail, "/api/v1/roles", "MANAGE_ROLES");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.PERMISSION_DENIED);
        assertThat(event.getResponseStatus()).isEqualTo(403);
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getOldValue()).isEqualTo("MANAGE_ROLES");
        assertThat(event.getActorEmail()).isEqualTo(actorEmail);
    }

    // ── Actor resolution ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Actor entity is populated from security context + user repository")
    void actorResolution_populatesActorEntity() {
        UUID roleId = UUID.randomUUID();
        rbacAuditService.recordRoleCreated(roleId, "FINANCE");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getActorEmail()).isEqualTo(actorEmail);
        assertThat(event.getActor()).isNotNull();
        assertThat(event.getActor().getId()).isEqualTo(userId);
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Save failure is swallowed — does not propagate to caller")
    void saveFailure_isSwallowed() {
        when(auditEventRepository.save(any())).thenThrow(new RuntimeException("DB unavailable"));

        // Should not throw
        rbacAuditService.recordRoleCreated(UUID.randomUUID(), "FINANCE");
    }
}
