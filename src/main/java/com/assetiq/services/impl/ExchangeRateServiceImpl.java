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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExchangeRateServiceImpl extends TenantAwareService implements ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateServiceImpl.class);

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateServiceImpl(ExchangeRateRepository exchangeRateRepository,
                                   OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public ExchangeRateDto create(ExchangeRateDto dto) {
        Organisation org = requireTenantOrg();

        ExchangeRate er = new ExchangeRate();
        er.setBaseCurrency(dto.getBaseCurrency().toUpperCase());
        er.setTargetCurrency(dto.getTargetCurrency().toUpperCase());
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
        if (fromCurrency == null || toCurrency == null) return amount;

        String from = fromCurrency.toUpperCase();
        String to   = toCurrency.toUpperCase();

        if (from.equals(to)) return amount;

        Organisation org = requireTenantOrg();
        LocalDate date   = asOf != null ? asOf : LocalDate.now();

        // Try direct rate first
        List<ExchangeRate> direct = exchangeRateRepository.findRateAsOf(org, from, to, date);
        if (!direct.isEmpty()) {
            BigDecimal rate = direct.get(0).getRate();
            return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        }

        // Try reverse rate (to→from) and take reciprocal
        List<ExchangeRate> reverse = exchangeRateRepository.findRateAsOf(org, to, from, date);
        if (!reverse.isEmpty()) {
            BigDecimal reverseRate = reverse.get(0).getRate();
            if (reverseRate.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal rate = BigDecimal.ONE.divide(reverseRate, new MathContext(10, RoundingMode.HALF_UP));
                return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
            }
        }

        log.warn("No exchange rate found for {}->{} as of {}. Returning original amount.", from, to, date);
        return amount;
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
