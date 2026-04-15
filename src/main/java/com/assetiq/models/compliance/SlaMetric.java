package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Monthly SLA / availability metrics for SOC 2 Availability criteria.
 */
@Entity
@Table(name = "sla_metric",
        uniqueConstraints = @UniqueConstraint(name = "uq_sla_org_month_year", columnNames = {"organisation_id", "month", "year"}),
        indexes = @Index(name = "idx_sla_org", columnList = "organisation_id,year,month"))
@Getter
@Setter
public class SlaMetric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    /** Uptime as a percentage, e.g. 99.95 */
    @Column(name = "uptime_percent", nullable = false)
    private Double uptimePercent;

    @Column(name = "planned_downtime_minutes")
    private Integer plannedDowntimeMinutes;

    @Column(name = "unplanned_downtime_minutes")
    private Integer unplannedDowntimeMinutes;

    @Column(name = "incident_count")
    private Integer incidentCount;

    /** Recovery Time Objective — actual RTO achieved in minutes */
    @Column(name = "rto_minutes")
    private Integer rtoMinutes;

    /** Recovery Point Objective — actual RPO achieved in minutes */
    @Column(name = "rpo_minutes")
    private Integer rpoMinutes;

    @Column(name = "sla_breached")
    private Boolean slaBreached = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
