package com.assetiq.services;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
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
 *       (defaults to "GHS").</li>
 * </ol>
 * <p>
 * Lookups are cheap (one indexed PK hit, typically cached by Hibernate) and
 * read-only, so no aggressive caching is layered in here.
 */
@Service
public class CurrencyResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrencyResolver.class);

    private final OrganisationRepository organisationRepository;
    private final String platformDefault;

    public CurrencyResolver(
            OrganisationRepository organisationRepository,
            @Value("${app.billing.default-currency:GHS}") String platformDefault) {
        this.organisationRepository = organisationRepository;
        this.platformDefault = normalise(platformDefault, "GHS");
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
        if (country == null) return "GHS";
        String norm = country.trim().toUpperCase(Locale.ROOT);
        return switch (norm) {
            case "NG", "NIGERIA" -> "NGN";
            case "KE", "KENYA" -> "KES";
            case "ZA", "SOUTH AFRICA" -> "ZAR";
            case "US", "USA", "UNITED STATES", "UNITED STATES OF AMERICA" -> "USD";
            case "GB", "UK", "UNITED KINGDOM" -> "GBP";
            // P1-12: CA added to keep the country picker on the portal/desktop
            // (which advertises CAD) in sync with what the backend actually
            // sets on the Organisation at registration time.
            case "CA", "CANADA" -> "CAD";
            case "DE", "FR", "IT", "ES", "NL", "BE", "IE", "PT", "AT", "FI",
                 "GERMANY", "FRANCE", "ITALY", "SPAIN", "NETHERLANDS",
                 "BELGIUM", "IRELAND", "PORTUGAL", "AUSTRIA", "FINLAND" -> "EUR";
            default -> "GHS"; // Ghana-first fallback.
        };
    }

    private static String normalise(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed.toUpperCase(Locale.ROOT);
    }
}
