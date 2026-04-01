package com.example.demo.controllers.v1;

import com.example.demo.dto.NotificationPageDto;
import com.example.demo.dto.NotificationPreferencesDto;
import com.example.demo.enums.NotificationType;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    public NotificationsController(NotificationService notificationService,
                                   UserRepository userRepository,
                                   OrganisationRepository organisationRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
    }

    /**
     * GET /api/v1/notifications
     * Query params:
     *   type   — optional: DEPRECATION | MAINTENANCE | APPROVAL | SYSTEM | TRANSFER | DISPOSAL | PURCHASE_ORDER
     *   status — "unread" | "read" | "all" (default: "all")
     *   limit  — max results, default 20
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<NotificationPageDto> getNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") long offset) {

        User user = requireUser();
        Organisation org = requireOrg();
        NotificationType notifType = parseType(type);
        Boolean read = parseStatus(status);
        return ResponseEntity.ok(notificationService.getNotifications(user, org, notifType, read, limit, offset));
    }

    /**
     * PATCH /api/v1/notifications/{notificationId}/read
     */
    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable UUID notificationId) {
        notificationService.markAsRead(notificationId, requireUser(), requireOrg());
        return ResponseEntity.ok(Map.of(
                "notificationId", notificationId,
                "read", true,
                "readAt", Instant.now().toString()));
    }

    /**
     * PATCH /api/v1/notifications/mark-all-read
     */
    @PatchMapping("/mark-all-read")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        int count = notificationService.markAllAsRead(requireUser(), requireOrg());
        return ResponseEntity.ok(Map.of(
                "markedAsRead", count,
                "markedAt", Instant.now().toString()));
    }

    /**
     * DELETE /api/v1/notifications/{notificationId}
     */
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<Void> deleteNotification(@PathVariable UUID notificationId) {
        notificationService.deleteNotification(notificationId, requireUser(), requireOrg());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/notifications
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAllNotifications() {
        int count = notificationService.deleteAllNotifications(requireUser(), requireOrg());
        return ResponseEntity.ok(Map.of(
                "deletedCount", count,
                "deletedAt", Instant.now().toString()));
    }

    /**
     * GET /api/v1/notifications/preferences
     */
    @GetMapping("/preferences")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<NotificationPreferencesDto> getNotificationPreferences() {
        return ResponseEntity.ok(notificationService.getPreferences(requireUser()));
    }

    /**
     * PATCH /api/v1/notifications/preferences
     */
    @PatchMapping("/preferences")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<NotificationPreferencesDto> updateNotificationPreferences(
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(notificationService.updatePreferences(requireUser(), request));
    }

    /**
     * GET /api/v1/notifications/summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> getNotificationSummary() {
        return ResponseEntity.ok(notificationService.getSummary(requireUser(), requireOrg()));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        String email = auth.getName();
        if (TenantContext.hasOrganisationId()) {
            return userRepository.findByEmailAndOrganisationId(email, TenantContext.getOrganisationId())
                    .orElseThrow(() -> new AccessDeniedException("User not found in organisation"));
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }

    private Organisation requireOrg() {
        if (!TenantContext.hasOrganisationId()) {
            throw new AccessDeniedException("Tenant context is required. Provide X-Organisation-Id header.");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(TenantContext.getOrganisationId())
                .orElseThrow(() -> new AccessDeniedException("Organisation not found"));
    }

    private NotificationType parseType(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            return NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid notification type: " + type +
                    ". Valid values: DEPRECATION, MAINTENANCE, APPROVAL, SYSTEM, TRANSFER, DISPOSAL, PURCHASE_ORDER");
        }
    }

    private Boolean parseStatus(String status) {
        return switch (status.toLowerCase()) {
            case "unread" -> false;
            case "read" -> true;
            default -> null;
        };
    }
}
