package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * ICS/IEC 62443 Security Zone based on the Purdue model (levels 0-5).
 * Level 0 = Field devices, Level 1 = Controllers, Level 2 = Supervisory,
 * Level 3 = Operations, Level 4 = Enterprise, Level 5 = DMZ/Internet
 */
@Entity
@Table(name = "security_zone",
        indexes = @Index(name = "idx_security_zone_org", columnList = "organisation_id"))
@Getter
@Setter
public class SecurityZone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "name", nullable = false)
    private String name;

    /** Purdue model level: 0–5 */
    @Column(name = "purdue_level", nullable = false)
    private Integer purdueLevel;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "allowed_protocols")
    private String allowedProtocols;

    @Column(name = "asset_count")
    private Integer assetCount = 0;

    @Column(name = "network_range")
    private String networkRange;
}
