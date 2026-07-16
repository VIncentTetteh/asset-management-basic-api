package com.assetiq.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * P0-8: Global feature-flag record.
 * <p>
 * Tenant-specific overrides live in {@link FeatureFlagOrganisation}. The
 * default answer for a tenant with no override is {@code enabledGlobally}.
 * <p>
 * Keys are namespaced dot-separated strings, e.g. {@code billing.ghs-default-currency}.
 */
@Entity
@Table(name = "feature_flag")
@Getter
@Setter
@NoArgsConstructor
public class FeatureFlag extends BaseEntity {

    @Column(name = "flag_key", nullable = false, unique = true, length = 120)
    private String key;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled_globally", nullable = false)
    private boolean enabledGlobally;

    /** 0..100. Used by {@code rolloutHash} evaluations when enabledGlobally=false. */
    @Column(name = "rollout_percentage", nullable = false)
    private short rolloutPercentage;
}
