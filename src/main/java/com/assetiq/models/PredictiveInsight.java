package com.assetiq.models;

import com.assetiq.enums.InsightSeverity;
import com.assetiq.enums.InsightType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "predictive_insight")
public class PredictiveInsight extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_type", nullable = false, length = 30)
    private InsightType insightType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private InsightSeverity severity;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * What this insight was concluded from, in words — "3 maintenance events in
     * the last 90 days; condition POOR", "warranty expiry 2026-11-04".
     *
     * <p>This replaced a fabricated confidence score. A heuristic's evidence is
     * more useful than a decimal, because the user can check it against the
     * record and disagree with the rule rather than with a number.
     */
    @Column(name = "basis", columnDefinition = "TEXT")
    private String basis;

    /**
     * Always null since V64.
     *
     * @deprecated There was never a model behind this. The evidence lives in
     *             {@link #getBasis()}. Retained only so a client still reading
     *             the column sees null rather than an error; it will be dropped
     *             once nothing reads it.
     */
    @Deprecated(forRemoval = true)
    @Column(name = "confidence")
    private Double confidence;

    /** Date the predicted event is expected to occur */
    @Column(name = "predicted_date")
    private LocalDate predictedDate;

    @Column(name = "resolved")
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
