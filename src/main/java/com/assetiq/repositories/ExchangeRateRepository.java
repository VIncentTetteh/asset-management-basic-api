package com.assetiq.repositories;

import com.assetiq.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    List<ExchangeRate> findByOrganisationAndDeletedAtIsNull(Organisation org);

    @Query("SELECT e FROM ExchangeRate e WHERE e.organisation = :org AND e.baseCurrency = :base AND e.targetCurrency = :target AND e.deletedAt IS NULL ORDER BY e.effectiveDate DESC")
    List<ExchangeRate> findLatestRates(@Param("org") Organisation org, @Param("base") String base, @Param("target") String target);

    @Query("SELECT e FROM ExchangeRate e WHERE e.organisation = :org AND e.baseCurrency = :base AND e.targetCurrency = :target AND e.effectiveDate <= :date AND e.deletedAt IS NULL ORDER BY e.effectiveDate DESC, e.createdAt DESC")
    List<ExchangeRate> findRateAsOf(@Param("org") Organisation org, @Param("base") String base, @Param("target") String target, @Param("date") LocalDate date);

    /** True when a live rate for this pair and effective date already exists (V51 keeps it unique). */
    boolean existsByOrganisationAndBaseCurrencyAndTargetCurrencyAndEffectiveDateAndDeletedAtIsNull(
            Organisation org, String baseCurrency, String targetCurrency, LocalDate effectiveDate);
}
