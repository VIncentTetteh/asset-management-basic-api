package com.assetiq.dpa.model;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(
    name = "consent_record",
    indexes = {
        @Index(name = "idx_consent_record_org_user_purpose",
               columnList = "organisation_id,user_id,purpose")
    }
)
@Getter @Setter
@ToString(onlyExplicitlyIncluded = true)
public class ConsentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ToString.Include
    @Column(nullable = false, length = 100)
    private String purpose;

    @ToString.Include
    @Column(nullable = false)
    private boolean granted;

    private Instant grantedAt;
    private Instant revokedAt;

    @Column(length = 50)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;
}
