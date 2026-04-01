package com.example.demo.services;

import com.example.demo.dto.NotificationPageDto;
import com.example.demo.dto.NotificationPreferencesDto;
import com.example.demo.enums.NotificationType;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    NotificationPageDto getNotifications(
            User user,
            Organisation org,
            NotificationType type,
            Boolean read,
            int limit,
            long offset
    );

    void markAsRead(UUID notificationId, User user, Organisation org);

    int markAllAsRead(User user, Organisation org);

    void deleteNotification(UUID notificationId, User user, Organisation org);

    int deleteAllNotifications(User user, Organisation org);

    NotificationPreferencesDto getPreferences(User user);

    NotificationPreferencesDto updatePreferences(User user, Map<String, Object> updates);

    Map<String, Object> getSummary(User user, Organisation org);

    /**
     * Create an in-app notification for every ORG_ADMIN in the given organisation.
     * Called by service impls to alert admins on CRUD and integration events.
     */
    void notifyOrgAdmins(Organisation org, NotificationType type,
                         String title, String message, UUID entityId, String actionUrl);
}
