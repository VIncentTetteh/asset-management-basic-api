package com.assetiq.models;

import com.assetiq.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_user_org", columnList = "user_id, organisation_id"),
        @Index(name = "idx_notification_read", columnList = "read, deleted_at")
})
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** UUID of the related entity (asset, PO, etc.) — nullable for system notifications */
    @Column(name = "entity_id")
    private UUID entityId;

    /** Relative API path for frontend deep-linking, e.g. /api/v1/assets/{id} */
    @Column(name = "action_url")
    private String actionUrl;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;
}
