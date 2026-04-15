package com.assetiq.dto;

import com.assetiq.enums.NotificationType;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class NotificationDto {
    private UUID notificationId;
    private NotificationType type;
    private String title;
    private String message;
    private UUID entityId;
    private String actionUrl;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
