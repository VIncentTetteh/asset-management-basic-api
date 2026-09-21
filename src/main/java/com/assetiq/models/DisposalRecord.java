package com.assetiq.models;

import com.assetiq.enums.DisposalMethod;
import com.assetiq.enums.DisposalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "disposal_record")
public class DisposalRecord extends BaseEntity {

    @ManyToOne
    @JoinColumn(nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisposalMethod disposalMethod;

    @Column(nullable = false)
    private LocalDate disposalDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal saleValue;

    /** ISO-4217 code of {@link #saleValue}; null on legacy rows, meaning the asset's currency. */
    @Column(length = 3)
    private String currency;

    /** The currency {@link #saleValue} is actually in: its own, else the asset's. */
    public String effectiveCurrency() {
        if (currency != null) return currency;
        return asset != null ? asset.getCurrency() : null;
    }

    /** Null only on rows written before V44, which were all effective at once. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DisposalStatus status = DisposalStatus.PENDING_APPROVAL;

    /** The maker; null on rows written before V44. */
    @ManyToOne
    private User requestedBy;

    /** The checker; null until approved. */
    @ManyToOne
    private User approvedBy;

    private Instant approvedAt;

    @ManyToOne
    private User rejectedBy;

    private Instant rejectedAt;

    /** True when this disposal actually took the asset off the books. */
    public boolean isEffective() {
        return status == null || status == DisposalStatus.APPROVED;
    }

    @Column(columnDefinition = "TEXT")
    private String reason;

    private String complianceDocumentUrl;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Organisation organisation;

}

