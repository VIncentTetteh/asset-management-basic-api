package com.assetiq.models;

import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "maintenance_record")
public class MaintenanceRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceType maintenanceType;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDate scheduledDate;

    private LocalDate performedDate;

    @ManyToOne
    private Supplier vendor;

    @Column(precision = 15, scale = 2)
    private BigDecimal cost;

    /** ISO-4217 code of {@link #cost}; null on legacy rows, meaning the asset's currency. */
    @Column(length = 3)
    private String currency;

    /** The currency {@link #cost} is actually in: its own, else the asset's. */
    public String effectiveCurrency() {
        if (currency != null) return currency;
        return asset != null ? asset.getCurrency() : null;
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MaintenanceStatus status = MaintenanceStatus.SCHEDULED;

    private LocalDate nextDueDate;

    /**
     * The asset's status when this ticket was opened, restored when it closes.
     * Null on tickets created before V53; those fall back to the assigned-user /
     * active-checkout rule.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status_before", length = 30)
    private com.assetiq.enums.AssetStatus assetStatusBefore;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}
