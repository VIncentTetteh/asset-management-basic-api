package com.assetiq.security;

import com.assetiq.enums.AuditEventType;
import com.assetiq.models.AuditEvent;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AuditEventRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes structured RBAC change events to the audit_event table.
 *
 * Each method runs in its own independent transaction (REQUIRES_NEW) so that
 * audit records are persisted even when the calling transaction rolls back (e.g.
 * a failed role update should still leave a trace that the attempt occurred).
 *
 * Recording is also @Async so the audit write never adds latency to the main
 * request path.  Failures are logged as WARN and swallowed — the audit trail is
 * best-effort; it must not break core business operations.
 */
@Service
public class RbacAuditService {

    private static final Logger log = LoggerFactory.getLogger(RbacAuditService.class);

    private static final String SYSTEM_PRINCIPAL = "SYSTEM";
    private static final int    MAX_VALUE_LENGTH  = 1000;

    private final AuditEventRepository  auditEventRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository         userRepository;

    public RbacAuditService(
            AuditEventRepository auditEventRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository) {
        this.auditEventRepository  = auditEventRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository         = userRepository;
    }

    // ── Role events ───────────────────────────────────────────────────────────

    /**
     * Records that a new role was created.
     *
     * @param roleId   the UUID of the newly created role
     * @param roleName human-readable name for the new_value snapshot
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleCreated(UUID roleId, String roleName) {
        persist(AuditEventType.ROLE_CREATED,
                roleId.toString(), null, roleName,
                "POST", "/roles");
    }

    /**
     * Records that a role's metadata was updated (name, description).
     *
     * @param roleId  the UUID of the role
     * @param oldName the role name before the update
     * @param newName the role name after the update
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleUpdated(UUID roleId, String oldName, String newName) {
        persist(AuditEventType.ROLE_UPDATED,
                roleId.toString(), oldName, newName,
                "PUT", "/roles/" + roleId);
    }

    /**
     * Records that a role's permission set changed.
     *
     * @param roleId   the UUID of the role
     * @param oldPerms the permission names before the change
     * @param newPerms the permission names after the change
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRolePermissionsChanged(UUID roleId,
                                             Collection<String> oldPerms,
                                             Collection<String> newPerms) {
        persist(AuditEventType.ROLE_PERMISSIONS_CHANGED,
                roleId.toString(),
                joinSorted(oldPerms),
                joinSorted(newPerms),
                "PUT", "/roles/" + roleId);
    }

    /**
     * Records that a role was deleted.
     *
     * @param roleId   the UUID of the deleted role
     * @param roleName its name at deletion time
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRoleDeleted(UUID roleId, String roleName) {
        persist(AuditEventType.ROLE_DELETED,
                roleId.toString(), roleName, null,
                "DELETE", "/roles/" + roleId);
    }

    // ── User-role events ──────────────────────────────────────────────────────

    /**
     * Records that a user's role assignment changed.
     *
     * @param userId      the UUID of the user
     * @param oldRoleName the role name before the change (null if first assignment)
     * @param newRoleName the role name after the change (null if role was removed)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUserRoleAssigned(UUID userId, String oldRoleName, String newRoleName) {
        persist(AuditEventType.USER_ROLE_ASSIGNED,
                userId.toString(), oldRoleName, newRoleName,
                "PUT", "/users/" + userId);
    }

    // ── Auth events ───────────────────────────────────────────────────────────

    /**
     * Records a permission-denied event (HTTP 403).
     *
     * @param actorEmail  the email of the requesting user (may be null if anonymous)
     * @param path        the request path that was denied
     * @param permission  the required permission that the actor lacked
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermissionDenied(String actorEmail, String path, String permission) {
        AuditEvent event = buildBase(AuditEventType.PERMISSION_DENIED, "GET", path);
        if (actorEmail != null) {
            event.setActorEmail(actorEmail);
            resolveActor(actorEmail).ifPresent(event::setActor);
        }
        event.setOldValue(permission);  // "old_value" holds the required permission name
        event.setResponseStatus(403);
        event.setSuccess(false);
        save(event);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void persist(AuditEventType type,
                         String targetId,
                         String oldValue, String newValue,
                         String method, String path) {
        try {
            AuditEvent event = buildBase(type, method, path);
            event.setTargetId(targetId);
            event.setOldValue(truncate(oldValue));
            event.setNewValue(truncate(newValue));
            event.setResponseStatus(200);
            event.setSuccess(true);
            save(event);
        } catch (Exception ex) {
            log.warn("[RBAC_AUDIT] Failed to persist {} event for target={}: {}",
                    type, targetId, ex.getMessage());
        }
    }

    private AuditEvent buildBase(AuditEventType type, String method, String path) {
        AuditEvent event = new AuditEvent();
        event.setEventType(type);
        event.setMethod(method);
        event.setPath(path != null ? truncatePath(path) : "INTERNAL");
        event.setSuccess(true);
        event.setResponseStatus(200);

        // Resolve actor from the current security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            event.setActorEmail(email);
            resolveActor(email).ifPresent(event::setActor);
        }

        // Resolve the current tenant's Organisation
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                    .ifPresent(event::setOrganisation);
        }

        return event;
    }

    private Optional<User> resolveActor(String email) {
        if (TenantContext.hasOrganisationId()) {
            UUID orgId = TenantContext.getOrganisationId();
            Optional<User> byOrg = userRepository.findByEmailAndOrganisationId(email, orgId);
            if (byOrg.isPresent()) return byOrg;
        }
        return userRepository.findByEmail(email);
    }

    private void save(AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("[RBAC_AUDIT] Failed to save audit event type={}: {}",
                    event.getEventType(), ex.getMessage());
        }
    }

    // ── Value formatters ──────────────────────────────────────────────────────

    private static String joinSorted(Collection<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().sorted().collect(Collectors.joining(", "));
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }

    private static String truncatePath(String path) {
        return path.length() <= 300 ? path : path.substring(0, 300);
    }
}
