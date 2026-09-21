package com.assetiq.services.impl;

import com.assetiq.models.ExchangeRate;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ExchangeRateRepository;
import com.assetiq.repositories.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateServiceImpl.findRate")
class ExchangeRateServiceImplTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 21);

    @Mock ExchangeRateRepository exchangeRateRepository;
    @Mock OrganisationRepository organisationRepository;

    private ExchangeRateServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateServiceImpl(exchangeRateRepository, organisationRepository);
        org = new Organisation();
        org.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("prefers the direct rate")
    void directRate() {
        when(exchangeRateRepository.findRateAsOf(org, "USD", "GHS", AS_OF))
                .thenReturn(List.of(rate("USD", "GHS", "15.25")));

        assertThat(service.findRate(org, "usd", "ghs", AS_OF)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("15.25"));
        verify(exchangeRateRepository, never()).findRateAsOf(org, "GHS", "USD", AS_OF);
    }

    @Test
    @DisplayName("falls back to the reciprocal of the inverse rate")
    void inverseRate() {
        when(exchangeRateRepository.findRateAsOf(org, "EUR", "GHS", AS_OF)).thenReturn(List.of());
        when(exchangeRateRepository.findRateAsOf(org, "GHS", "EUR", AS_OF))
                .thenReturn(List.of(rate("GHS", "EUR", "0.0625")));

        assertThat(service.findRate(org, "EUR", "GHS", AS_OF)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("16"));
    }

    @Test
    @DisplayName("returns empty instead of throwing when no rate exists")
    void missingRate() {
        when(exchangeRateRepository.findRateAsOf(org, "JPY", "GHS", AS_OF)).thenReturn(List.of());
        when(exchangeRateRepository.findRateAsOf(org, "GHS", "JPY", AS_OF)).thenReturn(List.of());

        assertThat(service.findRate(org, "JPY", "GHS", AS_OF)).isEmpty();
    }

    @Test
    @DisplayName("same currency is a rate of one with no query")
    void sameCurrency() {
        assertThat(service.findRate(org, "GHS", "ghs", AS_OF)).hasValue(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateRepository);
    }

    private ExchangeRate rate(String base, String target, String value) {
        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency(base);
        er.setTargetCurrency(target);
        er.setRate(new BigDecimal(value));
        er.setEffectiveDate(AS_OF.minusDays(1));
        er.setOrganisation(org);
        return er;
    }
}
