package com.assetiq.services.impl;

import com.assetiq.dto.ExchangeRateDto;
import com.assetiq.models.ExchangeRate;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ExchangeRateRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.ExchangeRateService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExchangeRateServiceImpl extends TenantAwareService implements ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateServiceImpl.class);

    /** Precision used when inverting a stored rate (16 significant digits). */
    private static final MathContext RECIPROCAL_PRECISION = MathContext.DECIMAL64;

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateServiceImpl(ExchangeRateRepository exchangeRateRepository,
                                   OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public ExchangeRateDto create(ExchangeRateDto dto) {
        Organisation org = requireTenantOrg();
        String base = dto.getBaseCurrency().trim().toUpperCase();
        String target = dto.getTargetCurrency().trim().toUpperCase();
        if (base.equals(target)) {
            throw new IllegalArgumentException("Base and target currencies must differ");
        }

        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency(base);
        er.setTargetCurrency(target);
        er.setRate(dto.getRate());
        er.setEffectiveDate(dto.getEffectiveDate() != null ? dto.getEffectiveDate() : LocalDate.now());
        er.setSource(dto.getSource() != null ? dto.getSource() : "MANUAL");
        er.setOrganisation(org);

        return toDto(exchangeRateRepository.save(er));
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangeRateDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        ExchangeRate er = exchangeRateRepository.findById(id)
                .filter(r -> r.getOrganisation().getId().equals(org.getId()) && r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found: " + id));
        return toDto(er);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExchangeRateDto> listAll() {
        Organisation org = requireTenantOrg();
        return exchangeRateRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        ExchangeRate er = exchangeRateRepository.findById(id)
                .filter(r -> r.getOrganisation().getId().equals(org.getId()) && r.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Exchange rate not found: " + id));
        er.setDeletedAt(Instant.now());
        exchangeRateRepository.save(er);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate asOf) {
        if (amount == null) return BigDecimal.ZERO;
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Source and target currency are required");
        }

        String from = fromCurrency.trim().toUpperCase(Locale.ROOT);
        String to   = toCurrency.trim().toUpperCase(Locale.ROOT);

        if (from.equals(to)) return amount;

        LocalDate date = asOf != null ? asOf : LocalDate.now();
        BigDecimal rate = findRate(requireTenantOrg(), from, to, date)
                .orElseThrow(() -> {
                    log.warn("No exchange rate found for {}->{} as of {}", from, to, date);
                    return new IllegalStateException(
                            "No approved exchange rate is available for " + from + " to " + to + " as of " + date);
                });
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findRate(Organisation organisation, String fromCurrency, String toCurrency,
                                         LocalDate asOf) {
        if (organisation == null || fromCurrency == null || toCurrency == null) {
            return Optional.empty();
        }
        String from = fromCurrency.trim().toUpperCase(Locale.ROOT);
        String to   = toCurrency.trim().toUpperCase(Locale.ROOT);
        if (from.equals(to)) return Optional.of(BigDecimal.ONE);

        LocalDate date = asOf != null ? asOf : LocalDate.now();

        // Direct rate first: one unit of FROM = rate units of TO.
        List<ExchangeRate> direct = exchangeRateRepository.findRateAsOf(organisation, from, to, date);
        if (!direct.isEmpty() && direct.get(0).getRate() != null
                && direct.get(0).getRate().signum() > 0) {
            return Optional.of(direct.get(0).getRate());
        }

        // Otherwise the reciprocal of the reverse (TO->FROM) rate.
        List<ExchangeRate> reverse = exchangeRateRepository.findRateAsOf(organisation, to, from, date);
        if (!reverse.isEmpty() && reverse.get(0).getRate() != null
                && reverse.get(0).getRate().signum() > 0) {
            return Optional.of(BigDecimal.ONE.divide(reverse.get(0).getRate(), RECIPROCAL_PRECISION));
        }
        return Optional.empty();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ExchangeRateDto toDto(ExchangeRate er) {
        ExchangeRateDto dto = new ExchangeRateDto();
        dto.setId(er.getId());
        dto.setBaseCurrency(er.getBaseCurrency());
        dto.setTargetCurrency(er.getTargetCurrency());
        dto.setRate(er.getRate());
        dto.setEffectiveDate(er.getEffectiveDate());
        dto.setSource(er.getSource());
        dto.setOrganisationId(er.getOrganisation().getId());
        return dto;
    }
}
