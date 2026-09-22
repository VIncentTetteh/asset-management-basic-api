package com.assetiq.models;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;


// One live rate per (organisation, pair, effective date): partial unique index
// uq_exchange_rates_org_pair_date_live (V51, WHERE deleted_at IS NULL). JPA cannot
// declare a partial index, so no @UniqueConstraint here.
@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
public class ExchangeRate extends BaseEntity {

    @Column(name = "base_currency", length = 3, nullable = false)
    private String baseCurrency;

    @Column(name = "target_currency", length = 3, nullable = false)
    private String targetCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "source", length = 50)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;
}
