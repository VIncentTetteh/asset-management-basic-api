package com.assetiq.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "notification_preferences", uniqueConstraints = {
        @UniqueConstraint(columnNames = "user_id", name = "uk_notif_prefs_user")
})
@Data
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationPreferences extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Per-type email toggles (stored as individual columns for query simplicity)
    @Column(nullable = false)
    private boolean emailDeprecation = true;

    @Column(nullable = false)
    private boolean emailMaintenance = true;

    @Column(nullable = false)
    private boolean emailApproval = true;

    @Column(nullable = false)
    private boolean emailSystem = false;

    @Column(nullable = false)
    private boolean emailTransfer = true;

    @Column(nullable = false)
    private boolean emailDisposal = true;

    @Column(nullable = false)
    private boolean emailPurchaseOrder = true;

    @Column(nullable = false)
    private boolean pushNotifications = true;

    @Column(nullable = false)
    private boolean inAppNotifications = true;

    @Column(nullable = false)
    private boolean dailyDigest = true;

    /** Time of day for daily digest, e.g. "09:00" */
    @Column(length = 5)
    private String digestTime = "09:00";
}
