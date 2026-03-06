package com.example.demo.controllers.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Notifications Controller
 * Handles user notifications and alerts
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationsController {

    /**
     * GET /api/v1/notifications
     * Get user notifications
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "unread") String status,
            @RequestParam(defaultValue = "20") int limit) {

        List<Map<String, Object>> notifications = new ArrayList<>();

        notifications.add(createNotification(
            "deprecation",
            "Asset Depreciation Notice",
            "Dell XPS 13 has completed its useful life",
            "/api/v1/assets/{asset_uuid}",
            false
        ));

        notifications.add(createNotification(
            "maintenance",
            "Scheduled Maintenance Due",
            "MacBook Pro requires scheduled maintenance",
            "/api/v1/assets/{asset_uuid}",
            false
        ));

        notifications.add(createNotification(
            "approval",
            "Purchase Order Requires Approval",
            "PO-2026-001 awaiting your approval",
            "/api/v1/purchase-orders/{po_uuid}",
            false
        ));

        notifications.add(createNotification(
            "system",
            "System Maintenance Scheduled",
            "System maintenance scheduled for 2026-03-10 at 2:00 AM",
            null,
            true
        ));

        Map<String, Object> response = new HashMap<>();
        response.put("totalNotifications", 4);
        response.put("unreadCount", 3);
        response.put("limit", limit);
        response.put("notifications", notifications);

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/notifications/{notification_id}/read
     * Mark notification as read
     */
    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> markAsRead(@PathVariable String notificationId) {

        Map<String, Object> response = new HashMap<>();
        response.put("notificationId", notificationId);
        response.put("read", true);
        response.put("readAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/notifications/mark-all-read
     * Mark all notifications as read
     */
    @PatchMapping("/mark-all-read")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> markAllAsRead() {

        Map<String, Object> response = new HashMap<>();
        response.put("markedAsRead", 3);
        response.put("markedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/notifications/{notification_id}
     * Delete notification
     */
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> deleteNotification(@PathVariable String notificationId) {
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/notifications
     * Delete all notifications
     */
    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> deleteAllNotifications() {

        Map<String, Object> response = new HashMap<>();
        response.put("deletedCount", 4);
        response.put("deletedAt", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/notifications/preferences
     * Get notification preferences
     */
    @GetMapping("/preferences")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getNotificationPreferences() {

        Map<String, Object> response = new HashMap<>();

        Map<String, Boolean> emailNotifications = new HashMap<>();
        emailNotifications.put("deprecation", true);
        emailNotifications.put("maintenance", true);
        emailNotifications.put("approval", true);
        emailNotifications.put("system", false);

        response.put("emailNotifications", emailNotifications);
        response.put("pushNotifications", true);
        response.put("inAppNotifications", true);
        response.put("dailyDigest", true);
        response.put("digestTime", "09:00");

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/notifications/preferences
     * Update notification preferences
     */
    @PatchMapping("/preferences")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> updateNotificationPreferences(@RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();
        response.put("updated", true);
        response.put("updatedAt", Instant.now().toString());
        response.put("preferences", request);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/notifications/summary
     * Get notification summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN')")
    public ResponseEntity<?> getNotificationSummary() {

        Map<String, Object> response = new HashMap<>();
        response.put("totalNotifications", 45);
        response.put("unreadNotifications", 3);
        response.put("today", 8);
        response.put("thisWeek", 22);

        Map<String, Integer> byType = new HashMap<>();
        byType.put("deprecation", 12);
        byType.put("maintenance", 15);
        byType.put("approval", 10);
        byType.put("system", 8);

        response.put("byType", byType);
        response.put("lastNotificationAt", "2026-03-05T10:30:00Z");

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> createNotification(String type, String title, String message, String actionUrl, boolean read) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", UUID.randomUUID().toString());
        notification.put("type", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("entityId", UUID.randomUUID().toString());
        notification.put("createdAt", Instant.now().minusSeconds(3600).toString());
        notification.put("read", read);
        if (actionUrl != null) {
            notification.put("actionUrl", actionUrl);
        }
        return notification;
    }
}

