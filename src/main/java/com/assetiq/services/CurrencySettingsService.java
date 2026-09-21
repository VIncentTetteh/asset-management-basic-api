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

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        return new CurrencySettingsDto(base,
                reachableCurrencies(base, exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(org),
                        LocalDate.now()),
                canEdit);
    }

    /**
     * Currencies an amount in {@code base} can actually be converted into, through any
     * chain of rates already in effect. Offering a currency that appears in some rate
     * but cannot be reached from the base made the display switcher look broken: the
     * button highlighted and every figure stayed in the original currency.
     */
    static List<String> reachableCurrencies(String base, List<ExchangeRate> rates, LocalDate asOf) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (ExchangeRate rate : rates) {
            if (rate.getEffectiveDate() != null && rate.getEffectiveDate().isAfter(asOf)) {
                continue;
            }
            if (rate.getRate() == null || rate.getRate().signum() <= 0) {
                continue;
            }
            String from = normalise(rate.getBaseCurrency());
            String to = normalise(rate.getTargetCurrency());
            if (from == null || to == null) {
                continue;
            }
            graph.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            graph.computeIfAbsent(to, k -> new HashSet<>()).add(from);
        }
        Set<String> reached = new TreeSet<>();
        Deque<String> queue = new ArrayDeque<>();
        reached.add(base);
        queue.add(base);
        while (!queue.isEmpty()) {
            for (String next : graph.getOrDefault(queue.poll(), Set.of())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        return new ArrayList<>(reached);
    }

    private static String normalise(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
