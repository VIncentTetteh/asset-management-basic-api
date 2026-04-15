package com.assetiq.services;

import com.assetiq.dto.ExchangeRateDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExchangeRateService {

    ExchangeRateDto create(ExchangeRateDto dto);

    ExchangeRateDto getById(UUID id);

    List<ExchangeRateDto> listAll();

    void delete(UUID id);

    /**
     * Convert {@code amount} from {@code fromCurrency} to {@code toCurrency}
     * using the closest exchange rate on or before {@code asOf}.
     * Returns {@code amount} unchanged if no rate is found.
     */
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate asOf);
}
