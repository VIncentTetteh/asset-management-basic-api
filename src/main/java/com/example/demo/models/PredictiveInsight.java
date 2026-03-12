package com.example.demo.models;

import com.example.demo.enums.InsightSeverity;
import com.example.demo.enums.InsightType;
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

    /** Confidence score 0.0–1.0 */
    @Column(name = "confidence")
    private double confidence;

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
