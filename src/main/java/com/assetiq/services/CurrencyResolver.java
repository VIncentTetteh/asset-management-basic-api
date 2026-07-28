package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P1-1 / P1-2: Single source of truth for the default currency a tenant should
 * see when a caller (controller, importer, seeder) doesn't pass one explicitly.
 * <p>
 * Order of resolution for {@link #defaultForCurrentTenant()}:
 * <ol>
 *   <li>Explicit user input (caller passes a non-blank currency → we don't
 *       reach the resolver).</li>
 *   <li>{@link Organisation#getBillingCurrency()} for the current tenant.</li>
 *   <li>Configured platform fallback {@code app.billing.default-currency}
 *       (defaults to "USD" — the global default; GHS and other currencies
 *       remain fully supported as per-tenant attributes).</li>
 * </ol>
 * <p>
 * Lookups are cheap (one indexed PK hit, typically cached by Hibernate) and
 * read-only, so no aggressive caching is layered in here.
 */
@Service
public class CurrencyResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrencyResolver.class);

    /** Global fallback when a country can't be resolved to a currency. */
    private static final String DEFAULT_CURRENCY = "USD";

    /**
     * Backward-compatibility aliases: common long-form / colloquial country
     * names → ISO-3166 alpha-2, for legacy free-text {@code country} values.
     * New writes come from the ISO country pickers as alpha-2, so this map only
     * needs to cover names that already exist in the data or that users type.
     */
    private static final Map<String, String> LONG_NAME_TO_ALPHA2 = Map.ofEntries(
            Map.entry("GHANA", "GH"),
            Map.entry("NIGERIA", "NG"),
            Map.entry("KENYA", "KE"),
            Map.entry("SOUTH AFRICA", "ZA"),
            Map.entry("USA", "US"),
            Map.entry("UNITED STATES", "US"),
            Map.entry("UNITED STATES OF AMERICA", "US"),
            Map.entry("UK", "GB"),
            Map.entry("UNITED KINGDOM", "GB"),
            Map.entry("CANADA", "CA"),
            Map.entry("GERMANY", "DE"),
            Map.entry("FRANCE", "FR"),
            Map.entry("ITALY", "IT"),
            Map.entry("SPAIN", "ES"),
            Map.entry("NETHERLANDS", "NL"),
            Map.entry("BELGIUM", "BE"),
            Map.entry("IRELAND", "IE"),
            Map.entry("PORTUGAL", "PT"),
            Map.entry("AUSTRIA", "AT"),
            Map.entry("FINLAND", "FI"),
            Map.entry("AUSTRALIA", "AU"),
            Map.entry("JAPAN", "JP"),
            Map.entry("INDIA", "IN"));

    private final OrganisationRepository organisationRepository;
    private final String platformDefault;

    public CurrencyResolver(
            OrganisationRepository organisationRepository,
            @Value("${app.billing.default-currency:USD}") String platformDefault) {
        this.organisationRepository = organisationRepository;
        this.platformDefault = normalise(platformDefault, "USD");
    }

    /** Resolve the tenant's default currency from the thread-local tenant context. */
    public String defaultForCurrentTenant() {
        UUID orgId = TenantContext.getOrganisationId();
        return defaultFor(orgId);
    }

    /** Resolve an explicit tenant's default currency (nullable — for background jobs). */
    @Transactional(readOnly = true)
    public String defaultFor(UUID organisationId) {
        if (organisationId == null) {
            return platformDefault;
        }
        Optional<Organisation> org = organisationRepository.findByIdAndDeletedAtIsNull(organisationId);
        return org.map(Organisation::getBillingCurrency)
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.toUpperCase(Locale.ROOT))
                .orElseGet(() -> {
                    log.debug("[Currency] Org {} has no billing_currency — falling back to {}.",
                            organisationId, platformDefault);
                    return platformDefault;
                });
    }

    /**
     * If {@code supplied} is blank/null, resolve the tenant default. Otherwise
     * normalise to uppercase ISO-4217 3-letter form and return. This is the
     * helper service impls call on the create path: {@code resolveOrDefault(dto.getCurrency())}.
     */
    public String resolveOrDefault(String supplied) {
        if (supplied != null && !supplied.isBlank()) {
            return supplied.trim().toUpperCase(Locale.ROOT);
        }
        return defaultForCurrentTenant();
    }

    /**
     * Derive the default currency from an ISO-alpha-2 country code, used by
     * {@code TenantRegistrationService} when the customer signs up.
     */
    public static String currencyForCountry(String country) {
        // Global default is USD; a recognised country maps to its local currency
        // via the JDK's built-in ISO-3166 → ISO-4217 table (e.g. GH → GHS,
        // AU → AUD, JP → JPY), so *every* country resolves correctly — not just a
        // hand-maintained short list. Callers should send an ISO-3166 alpha-2
        // code (the web/portal country pickers do); common long-form names are
        // still accepted for backward-compatibility with legacy free-text data.
        if (country == null || country.isBlank()) return DEFAULT_CURRENCY;
        String norm = country.trim().toUpperCase(Locale.ROOT);
        String alpha2 = LONG_NAME_TO_ALPHA2.getOrDefault(norm, norm);
        if (alpha2.length() != 2) return DEFAULT_CURRENCY;
        try {
            Currency currency = Currency.getInstance(Locale.of("", alpha2));
            return currency != null ? currency.getCurrencyCode() : DEFAULT_CURRENCY;
        } catch (IllegalArgumentException e) {
            // Not a supported ISO-3166 country code, or a territory with no
            // currency of its own — fall back to the global default.
            return DEFAULT_CURRENCY;
        }
    }

    private static String normalise(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed.toUpperCase(Locale.ROOT);
    }
}
