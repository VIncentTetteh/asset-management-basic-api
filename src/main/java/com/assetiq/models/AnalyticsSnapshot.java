package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What was true about one tenant's estate on one day.
 *
 * <p>Written once by {@code AnalyticsSnapshotJob} and never updated: a trend
 * built from these rows is a record of what happened, not a reconstruction of
 * what today's data implies about the past. Amounts are in {@link #currency},
 * the tenant's base currency on the snapshot date.
 */
@Entity
@Table(name = "analytics_snapshot")
@Getter
@Setter
public class AnalyticsSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(length = 3, nullable = false)
    private String currency;

    /** False when at least one amount was left out for want of an exchange rate. */
    @Column(nullable = false)
    private boolean complete = true;

    @Column(name = "asset_count", nullable = false)
    private long assetCount;

    @Column(name = "active_asset_count", nullable = false)
    private long activeAssetCount;

    /**
     * Assets in stock or reserved that nobody is recorded as having seen for
     * {@code CostWasteService.DEFAULT_IDLE_DAYS} days — a sighting being a scan,
     * a checkout or check-in, or an audit verification.
     *
     * <p>The column is still called {@code idle_asset_count}: it was created one
     * migration ago under that name, migrations here are expand-only, and
     * renaming a column to fix a word is not worth a rewrite. The name in the
     * API and in this field is the accurate one.
     */
    @Column(name = "idle_asset_count", nullable = false)
    private long notSeenAssetCount;

    @Column(name = "unassigned_in_use_count", nullable = false)
    private long unassignedInUseCount;

    @Column(name = "fully_depreciated_count", nullable = false)
    private long fullyDepreciatedCount;

    @Column(name = "total_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "net_book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal netBookValue = BigDecimal.ZERO;

    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "monthly_depreciation", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyDepreciation = BigDecimal.ZERO;

    @Column(name = "overdue_maintenance_count", nullable = false)
    private long overdueMaintenanceCount;

    @Column(name = "licence_seats_total", nullable = false)
    private long licenceSeatsTotal;

    @Column(name = "licence_seats_used", nullable = false)
    private long licenceSeatsUsed;
}
