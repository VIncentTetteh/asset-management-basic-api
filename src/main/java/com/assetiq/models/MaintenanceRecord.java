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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MaintenanceStatus status = MaintenanceStatus.SCHEDULED;

    private LocalDate nextDueDate;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}
