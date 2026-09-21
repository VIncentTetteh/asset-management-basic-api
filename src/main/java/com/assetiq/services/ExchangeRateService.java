package com.assetiq.services;

import com.assetiq.dto.ExchangeRateDto;
import com.assetiq.models.Organisation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateService {

    ExchangeRateDto create(ExchangeRateDto dto);

    ExchangeRateDto getById(UUID id);

    List<ExchangeRateDto> listAll();

    void delete(UUID id);

    /**
     * Convert {@code amount} from {@code fromCurrency} to {@code toCurrency} for the
     * current tenant, using the latest rate whose effective date is on or before
     * {@code asOf} (today when {@code asOf} is null). A direct FROM->TO rate is
     * preferred; otherwise the reciprocal of a TO->FROM rate is used. The result is
     * rounded to 4 decimal places (HALF_UP).
     *
     * @return {@code amount} unchanged when both currencies are the same, and
     *         {@link BigDecimal#ZERO} when {@code amount} is null
     * @throws IllegalStateException when no direct or inverse rate exists - the
     *         amount is never returned unconverted across currencies
     * @throws IllegalArgumentException when either currency is null
     */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate asOf);

    /**
     * Look up the multiplier that converts one unit of {@code fromCurrency} into
     * {@code toCurrency} for {@code organisation}, using the same resolution rules as
     * {@link #convert}: latest direct rate on or before {@code asOf}, else the
     * reciprocal of the latest inverse rate. Never throws for a missing rate.
     *
     * @return the rate ({@code 1} when the currencies are equal), or empty when no
     *         usable rate exists
     */
    Optional<BigDecimal> findRate(Organisation organisation, String fromCurrency, String toCurrency, LocalDate asOf);
}
