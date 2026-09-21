package com.assetiq.services;

import com.assetiq.dto.CurrencySettingsDto;
import com.assetiq.models.ExchangeRate;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ExchangeRateRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.RbacAuditService;
import com.assetiq.services.money.MoneyAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads and changes the tenant base (reporting) currency, stored as
 * {@link Organisation#getBillingCurrency()}.
 *
 * <p>The base currency is what dashboard, analytics, budget and cloud-cost totals
 * are converted into. Changing it does not change what the tenant is charged:
 * subscription checkout prices in the plan's own currency, not this field.
 *
 * <p>Always operates on the current tenant from the request context - never on
 * an organisation id supplied by the caller.
 */
@Service
public class CurrencySettingsService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(CurrencySettingsService.class);

    /** Mirrors the {@code @PreAuthorize} on organisation updates. */
    public static final Set<String> EDIT_AUTHORITIES =
            Set.of("ROLE_ADMIN", "ROLE_ORG_ADMIN", "MANAGE_ORGANIZATION_SETTINGS");

    private final ExchangeRateRepository exchangeRateRepository;
    private final MoneyAggregator moneyAggregator;
    private final RbacAuditService auditService;

    public CurrencySettingsService(OrganisationRepository organisationRepository,
                                   ExchangeRateRepository exchangeRateRepository,
                                   MoneyAggregator moneyAggregator,
                                   RbacAuditService auditService) {
        super(organisationRepository);
        this.exchangeRateRepository = exchangeRateRepository;
        this.moneyAggregator = moneyAggregator;
        this.auditService = auditService;
    }

    /** True when {@code authentication} holds any authority allowed to edit the base currency. */
    public static boolean canEdit(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(EDIT_AUTHORITIES::contains);
    }

    @Transactional(readOnly = true)
    public CurrencySettingsDto get(boolean canEdit) {
        return toDto(requireTenantOrg(), canEdit);
    }

    /**
     * Set the current tenant's base currency. Callers are already authorised to
     * edit (the endpoint is guarded), so the returned settings report {@code canEdit=true}.
     *
     * @throws IllegalArgumentException when {@code baseCurrency} is not ISO-4217
     */
    @Transactional
    public CurrencySettingsDto updateBaseCurrency(String baseCurrency) {
        String code = CurrencyResolver.normaliseIsoCode(baseCurrency);
        Organisation org = requireTenantOrg();
        String previous = org.getBillingCurrency();
        if (!code.equals(previous)) {
            org.setBillingCurrency(code);
            organisationRepository.save(org);
            auditService.recordBaseCurrencyChanged(org.getId(), previous, code);
            log.info("[Currency] Base currency for org {} changed from {} to {}", org.getId(), previous, code);
        }
        // Dashboard/analytics aggregates are computed per request (no cache to evict).
        return toDto(org, true);
    }

    private CurrencySettingsDto toDto(Organisation org, boolean canEdit) {
        String base = moneyAggregator.baseCurrencyOf(org);
        Set<String> currencies = new TreeSet<>();
        currencies.add(base);
        for (ExchangeRate rate : exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(org)) {
            addIfPresent(currencies, rate.getBaseCurrency());
            addIfPresent(currencies, rate.getTargetCurrency());
        }
        return new CurrencySettingsDto(base, new ArrayList<>(currencies), canEdit);
    }

    private static void addIfPresent(Set<String> target, String code) {
        if (code != null && !code.isBlank()) {
            target.add(code.trim().toUpperCase(Locale.ROOT));
        }
    }
}
