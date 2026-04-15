package com.assetiq.dto;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationPreferencesDto {
    /** Per-type email toggle map, keyed by lower-case NotificationType name */
    private Map<String, Boolean> emailNotifications;
    private boolean pushNotifications;
    private boolean inAppNotifications;
    private boolean dailyDigest;
    private String digestTime;
}
