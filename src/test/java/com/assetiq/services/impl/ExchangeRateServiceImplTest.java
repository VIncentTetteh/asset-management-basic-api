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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @org.junit.jupiter.api.Nested
    @DisplayName("create")
    class Create {
        @BeforeEach
        void tenant() {
            com.assetiq.multitenancy.TenantContext.setOrganisationId(org.getId());
            when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(java.util.Optional.of(org));
        }

        @org.junit.jupiter.api.AfterEach
        void clear() {
            com.assetiq.multitenancy.TenantContext.clear();
        }

        private com.assetiq.dto.ExchangeRateDto dto(String base, String target) {
            com.assetiq.dto.ExchangeRateDto dto = new com.assetiq.dto.ExchangeRateDto();
            dto.setBaseCurrency(base);
            dto.setTargetCurrency(target);
            dto.setRate(new BigDecimal("0.00654321"));
            dto.setEffectiveDate(AS_OF);
            dto.setSource(" ");
            return dto;
        }

        @Test
        @DisplayName("a code that is not ISO-4217 is a field error")
        void notIso() {
            assertThatThrownBy(() -> service.create(dto("ABC", "GHS")))
                    .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                    .extracting("field").isEqualTo("baseCurrency");
        }

        @Test
        @DisplayName("base equal to target is a targetCurrency field error")
        void samePair() {
            assertThatThrownBy(() -> service.create(dto("ghs", "GHS")))
                    .isInstanceOf(com.assetiq.exceptions.FieldValidationException.class)
                    .extracting("field").isEqualTo("targetCurrency");
        }

        @Test
        @DisplayName("a second live rate for the pair and date is a DUPLICATE on effectiveDate")
        void duplicate() {
            when(exchangeRateRepository.existsByOrganisationAndBaseCurrencyAndTargetCurrencyAndEffectiveDateAndDeletedAtIsNull(
                    org, "JPY", "GHS", AS_OF)).thenReturn(true);
            assertThatThrownBy(() -> service.create(dto("jpy", "ghs")))
                    .isInstanceOf(com.assetiq.exceptions.DuplicateFieldException.class)
                    .extracting("field").isEqualTo("effectiveDate");
        }

        @Test
        @DisplayName("stores normalised codes, the full 8-decimal rate and MANUAL for a blank source")
        void stores() {
            when(exchangeRateRepository.save(any(ExchangeRate.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            com.assetiq.dto.ExchangeRateDto saved = service.create(dto("jpy", "ghs"));
            assertThat(saved.getBaseCurrency()).isEqualTo("JPY");
            assertThat(saved.getRate()).isEqualByComparingTo("0.00654321");
            assertThat(saved.getSource()).isEqualTo("MANUAL");
        }
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
