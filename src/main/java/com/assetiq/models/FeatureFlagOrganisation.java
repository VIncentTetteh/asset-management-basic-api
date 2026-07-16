package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * P0-8: Per-organisation override for a {@link FeatureFlag}.
 * <p>
 * Evaluation order inside {@code FeatureFlagService}:
 * <ol>
 *   <li>If an override row exists for (flag, org), use its {@code enabled} value.</li>
 *   <li>Otherwise, fall back to {@link FeatureFlag#isEnabledGlobally()}.</li>
 * </ol>
 */
@Entity
@Table(
    name = "feature_flag_organisation",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_flag_org",
        columnNames = {"feature_flag_id", "organisation_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlagOrganisation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_flag_id", nullable = false)
    private FeatureFlag featureFlag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
