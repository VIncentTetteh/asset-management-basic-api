package com.assetiq.models.compliance;

import com.assetiq.models.BaseEntity;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "security_policy",
        indexes = @Index(name = "idx_policy_org", columnList = "organisation_id"))
@Getter
@Setter
public class SecurityPolicy extends BaseEntity {

    public enum PolicyStatus { DRAFT, UNDER_REVIEW, APPROVED, RETIRED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "version", length = 16)
    private String version;

    @Column(name = "document_url")
    private String documentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "approved_by_email")
    private String approvedByEmail;

    @Column(name = "effective_date")
    private Instant effectiveDate;

    @Column(name = "review_due_date")
    private Instant reviewDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PolicyStatus status = PolicyStatus.DRAFT;
}
