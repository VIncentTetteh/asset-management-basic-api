package com.assetiq.services;

import com.assetiq.dto.CurrencySettingsDto;
import com.assetiq.models.ExchangeRate;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.ExchangeRateRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.RbacAuditService;
import com.assetiq.services.money.MoneyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencySettingsService")
class CurrencySettingsServiceTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock ExchangeRateRepository exchangeRateRepository;
    @Mock ExchangeRateService exchangeRateService;
    @Mock CurrencyResolver currencyResolver;
    @Mock RbacAuditService auditService;

    private CurrencySettingsService service;
    private Organisation tenant;
    private Organisation otherTenant;

    @BeforeEach
    void setUp() {
        service = new CurrencySettingsService(organisationRepository, exchangeRateRepository,
                new MoneyAggregator(exchangeRateService, currencyResolver), auditService);
        tenant = org("GHS");
        otherTenant = org("EUR");
        TenantContext.setOrganisationId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET returns the base currency plus every currency in this org's rates, sorted")
    void get_returnsBaseAndRateCurrencies() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(tenant.getId())).thenReturn(Optional.of(tenant));
        when(exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(tenant)).thenReturn(List.of(
                rate("USD", "GHS"), rate("GHS", "eur"), rate("GBP", "USD")));

        CurrencySettingsDto dto = service.get(false);

        assertThat(dto.baseCurrency()).isEqualTo("GHS");
        assertThat(dto.availableCurrencies()).containsExactly("EUR", "GBP", "GHS", "USD");
        assertThat(dto.canEdit()).isFalse();
    }

    @Test
    @DisplayName("GET omits currencies no chain of rates reaches from the base")
    void get_omitsUnreachableCurrencies() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(tenant.getId())).thenReturn(Optional.of(tenant));
        // EUR<->CHF is an island: offering either would be a switch that does nothing.
        when(exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(tenant)).thenReturn(List.of(
                rate("USD", "GHS"), rate("EUR", "CHF")));

        assertThat(service.get(false).availableCurrencies()).containsExactly("GHS", "USD");
    }

    @Test
    @DisplayName("GET ignores rates that only take effect in the future")
    void get_ignoresFutureDatedRates() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(tenant.getId())).thenReturn(Optional.of(tenant));
        ExchangeRate future = rate("USD", "GHS");
        future.setEffectiveDate(java.time.LocalDate.now().plusDays(3));
        when(exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(tenant)).thenReturn(List.of(future));

        assertThat(service.get(false).availableCurrencies()).containsExactly("GHS");
    }

    @Test
    @DisplayName("PUT normalises the code, updates only the current tenant and audits the change")
    void update_changesCurrentTenantOnly() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(tenant.getId())).thenReturn(Optional.of(tenant));
        when(exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(tenant)).thenReturn(List.of());

        CurrencySettingsDto dto = service.updateBaseCurrency(" usd ");

        assertThat(tenant.getBillingCurrency()).isEqualTo("USD");
        assertThat(otherTenant.getBillingCurrency()).isEqualTo("EUR");
        verify(organisationRepository).save(tenant);
        verify(organisationRepository, never()).findByIdAndDeletedAtIsNull(otherTenant.getId());
        verify(auditService).recordBaseCurrencyChanged(tenant.getId(), "GHS", "USD");
        assertThat(dto.baseCurrency()).isEqualTo("USD");
        assertThat(dto.availableCurrencies()).containsExactly("USD");
        assertThat(dto.canEdit()).isTrue();
    }

    @Test
    @DisplayName("PUT with the current value is a no-op (no save, no audit)")
    void update_sameCurrencyIsNoop() {
        when(organisationRepository.findByIdAndDeletedAtIsNull(tenant.getId())).thenReturn(Optional.of(tenant));
        when(exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(tenant)).thenReturn(List.of());

        service.updateBaseCurrency("ghs");

        verify(organisationRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("PUT rejects non-ISO codes before touching any organisation")
    void update_rejectsInvalidCodes() {
        for (String bad : List.of("", "US", "DOLLAR", "ZZZ", "12$")) {
            assertThatThrownBy(() -> service.updateBaseCurrency(bad))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(organisationRepository, auditService);
    }

    @Test
    @DisplayName("PUT without a tenant context is refused")
    void update_requiresTenantContext() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.updateBaseCurrency("USD")).isInstanceOf(AccessDeniedException.class);
        verify(organisationRepository, never()).save(any());
    }

    @Test
    @DisplayName("canEdit mirrors the organisation-update authorities")
    void canEdit_mirrorsOrganisationUpdateAuthorities() {
        assertThat(CurrencySettingsService.canEdit(auth("ROLE_ORG_ADMIN"))).isTrue();
        assertThat(CurrencySettingsService.canEdit(auth("ROLE_ADMIN"))).isTrue();
        assertThat(CurrencySettingsService.canEdit(auth("MANAGE_ORGANIZATION_SETTINGS"))).isTrue();
        assertThat(CurrencySettingsService.canEdit(auth("ROLE_USER", "VIEW_ASSETS"))).isFalse();
        assertThat(CurrencySettingsService.canEdit(null)).isFalse();
    }

    private static UsernamePasswordAuthenticationToken auth(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated("u@example.com", "n/a",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static Organisation org(String currency) {
        Organisation o = new Organisation();
        o.setId(UUID.randomUUID());
        o.setBillingCurrency(currency);
        return o;
    }

    private ExchangeRate rate(String base, String target) {
        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency(base);
        er.setTargetCurrency(target);
        er.setRate(BigDecimal.TEN);
        er.setOrganisation(tenant);
        return er;
    }
}
