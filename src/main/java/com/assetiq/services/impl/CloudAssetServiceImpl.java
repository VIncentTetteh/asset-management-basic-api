package com.assetiq.services.impl;

import com.assetiq.cloudsync.CloudSyncDispatcher;
import com.assetiq.dto.CloudAssetDto;
import com.assetiq.dto.CloudCostRecordDto;
import com.assetiq.dto.CloudCostSummaryDto;
import com.assetiq.enums.CloudEnvironment;
import com.assetiq.enums.CloudProvider;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.CloudCostRecord;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CloudAssetRepository;
import com.assetiq.repositories.CloudCostRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CloudAssetService;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CloudAssetServiceImpl extends TenantAwareService implements CloudAssetService {

    private static final Logger log = LoggerFactory.getLogger(CloudAssetServiceImpl.class);

    private final CloudAssetRepository cloudAssetRepo;
    private final CloudCostRecordRepository costRepo;
    private final CurrencyResolver currencyResolver;
    private final CloudSyncDispatcher cloudSyncDispatcher;
    private final MoneyAggregator moneyAggregator;

    private static final int TOP_ASSET_COUNT = 5;

    /** Decides the "current month" of the cost summary; replaceable in tests. */
    private java.time.Clock clock = java.time.Clock.systemUTC();

    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    /**
     * Provider sync reads inventory with the <em>server's</em> cloud credentials
     * (instance role / env / service account), not the tenant's. On the hosted
     * multi-tenant service that is the platform's own account, so any tenant
     * admin could import the platform's infrastructure into their tenant. Off
     * unless a single-customer (standalone) deployment turns it on.
     */
    @org.springframework.beans.factory.annotation.Value("${app.cloud.sync.enabled:false}")
    private boolean syncEnabled;

    void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public CloudAssetServiceImpl(OrganisationRepository organisationRepository,
                                 CloudAssetRepository cloudAssetRepo,
                                 CloudCostRecordRepository costRepo,
                                 CurrencyResolver currencyResolver,
                                 CloudSyncDispatcher cloudSyncDispatcher,
                                 MoneyAggregator moneyAggregator) {
        super(organisationRepository);
        this.cloudAssetRepo = cloudAssetRepo;
        this.costRepo = costRepo;
        this.currencyResolver = currencyResolver;
        this.cloudSyncDispatcher = cloudSyncDispatcher;
        this.moneyAggregator = moneyAggregator;
    }

    @Override
    public CloudAssetDto create(CloudAssetDto dto) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = new CloudAsset();
        mapToEntity(dto, asset, org);
        asset.setLastSyncAt(Instant.now());
        return toDto(cloudAssetRepo.save(asset));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CloudAssetDto> list(String provider, String environment, Pageable pageable) {
        Organisation org = requireTenantOrg();
        List<CloudAsset> all = cloudAssetRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<CloudAssetDto> filtered = all.stream()
                .filter(a -> provider == null || provider.isBlank()
                        || a.getProvider().name().equalsIgnoreCase(provider))
                .filter(a -> environment == null || environment.isBlank()
                        || Objects.equals(CloudEnvironment.normaliseToName(environment),
                                CloudEnvironment.normaliseToName(a.getEnvironment())))
                .map(this::toDto)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<CloudAssetDto> pageContent = start >= filtered.size()
                ? Collections.emptyList() : filtered.subList(start, end);
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Override
    @Transactional(readOnly = true)
    public CloudAssetDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cloud asset not found: " + id));
        return toDto(asset);
    }

    @Override
    public CloudAssetDto update(UUID id, CloudAssetDto dto) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cloud asset not found: " + id));
        mapToEntity(dto, asset, org);
        asset.setLastSyncAt(Instant.now());
        return toDto(cloudAssetRepo.save(asset));
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cloud asset not found: " + id));
        asset.setDeletedAt(Instant.now());
        cloudAssetRepo.save(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public CloudCostSummaryDto getCostSummary() {
        Organisation org = requireTenantOrg();
        List<CloudAsset> assets = cloudAssetRepo.findByOrganisationAndDeletedAtIsNull(org);

        // Rule: for the current month (UTC), an asset with recorded costs counts at
        // the sum of those actuals (all services); an asset without any counts at its
        // monthly estimate. Actuals win because they are what was billed.
        LocalDate currentMonth = LocalDate.now(clock).withDayOfMonth(1);
        Map<UUID, List<CloudCostRecord>> actuals = costRepo
                .findByOrganisationAndBillingMonthAndDeletedAtIsNull(org, currentMonth).stream()
                .filter(r -> r.getCloudAsset() != null && r.getAmount() != null)
                .collect(Collectors.groupingBy(r -> r.getCloudAsset().getId()));

        // Convert every amount into the tenant base currency before summing or
        // ranking; assets whose currency has no rate are excluded and reported.
        CurrencyConversion fx = moneyAggregator.begin(org);
        MoneyAccumulator total = fx.newAccumulator();
        Map<CloudProvider, MoneyAccumulator> byProviderAcc = new EnumMap<>(CloudProvider.class);
        Map<String, MoneyAccumulator> byEnvAcc = new LinkedHashMap<>();
        List<Map.Entry<CloudAsset, BigDecimal>> converted = new ArrayList<>();
        int assetsWithActuals = 0;

        for (CloudAsset a : assets) {
            List<Map.Entry<BigDecimal, String>> amounts = monthlyAmounts(a, actuals.get(a.getId()));
            if (amounts.isEmpty()) continue;
            Optional<BigDecimal> inBase = toBase(fx, amounts);
            if (inBase.isEmpty()) continue;
            if (actuals.containsKey(a.getId())) assetsWithActuals++;
            converted.add(Map.entry(a, inBase.get()));
            for (Map.Entry<BigDecimal, String> m : amounts) {
                total.add(m.getKey(), m.getValue());
                if (a.getProvider() != null) {
                    byProviderAcc.computeIfAbsent(a.getProvider(), k -> fx.newAccumulator()).add(m.getKey(), m.getValue());
                }
                if (a.getEnvironment() != null) {
                    byEnvAcc.computeIfAbsent(a.getEnvironment().toUpperCase(), k -> fx.newAccumulator())
                            .add(m.getKey(), m.getValue());
                }
            }
        }

        Map<CloudProvider, BigDecimal> byProvider = new EnumMap<>(CloudProvider.class);
        byProviderAcc.forEach((k, acc) -> byProvider.put(k, acc.amount()));
        Map<String, BigDecimal> byEnv = new LinkedHashMap<>();
        byEnvAcc.forEach((k, acc) -> byEnv.put(k, acc.amount()));

        List<CloudCostSummaryDto.CloudAssetCostEntry> topAssets = converted.stream()
                .sorted(Map.Entry.<CloudAsset, BigDecimal>comparingByValue().reversed())
                .limit(TOP_ASSET_COUNT)
                .map(entry -> {
                    CloudAsset a = entry.getKey();
                    CloudCostSummaryDto.CloudAssetCostEntry e = new CloudCostSummaryDto.CloudAssetCostEntry();
                    e.setAssetName(a.getName());
                    e.setResourceType(a.getResourceType() != null ? a.getResourceType().name() : null);
                    e.setMonthlyCost(CurrencyConversion.round(entry.getValue()));
                    return e;
                })
                .collect(Collectors.toList());

        CloudCostSummaryDto summary = new CloudCostSummaryDto();
        summary.setTotalMonthlyCost(total.amount());
        summary.setCurrency(fx.baseCurrency());
        summary.setComplete(fx.isComplete());
        summary.setMissingRates(fx.missingRates());
        summary.setCostByProvider(byProvider);
        summary.setCostByEnvironment(byEnv);
        summary.setTopAssets(topAssets);
        summary.setActualsMonth(YearMonth.from(currentMonth).toString());
        summary.setAssetsWithActuals(assetsWithActuals);
        return summary;
    }

    /** This month's amounts for an asset: its recorded actuals, else its estimate. */
    private static List<Map.Entry<BigDecimal, String>> monthlyAmounts(CloudAsset a, List<CloudCostRecord> actuals) {
        if (actuals != null && !actuals.isEmpty()) {
            return actuals.stream()
                    .map(r -> Map.entry(r.getAmount(), r.getCurrency() != null ? r.getCurrency() : a.getCurrency()))
                    .collect(Collectors.toList());
        }
        if (a.getMonthlyCostEstimate() == null) return List.of();
        return List.of(Map.entry(a.getMonthlyCostEstimate(), a.getCurrency()));
    }

    /** Sum in the base currency, or empty when any amount has no exchange rate. */
    private static Optional<BigDecimal> toBase(CurrencyConversion fx, List<Map.Entry<BigDecimal, String>> amounts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, String> m : amounts) {
            Optional<BigDecimal> v = fx.toBase(m.getKey(), m.getValue());
            if (v.isEmpty()) return Optional.empty();
            sum = sum.add(v.get());
        }
        return Optional.of(sum);
    }

    @Override
    public void recordMonthlyCost(UUID assetId, String billingMonth, BigDecimal amount, String serviceName) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cloud asset not found: " + assetId));

        if (billingMonth == null || !billingMonth.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("billingMonth must be YYYY-MM");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or more");
        }
        LocalDate month = LocalDate.parse(billingMonth + "-01");
        String service = serviceName == null || serviceName.isBlank() ? null : serviceName.trim();
        if (service != null && service.length() > 200) {
            throw new IllegalArgumentException("serviceName must be at most 200 characters");
        }

        // Upsert on (asset, month, service): recording a month again corrects it
        // instead of adding a second row that the summary would double count.
        CloudCostRecord record = (service == null
                ? costRepo.findFirstByCloudAssetAndBillingMonthAndServiceNameIsNullAndDeletedAtIsNull(asset, month)
                : costRepo.findFirstByCloudAssetAndBillingMonthAndServiceNameAndDeletedAtIsNull(asset, month, service))
                .orElseGet(CloudCostRecord::new);
        record.setCloudAsset(asset);
        record.setBillingMonth(month);
        record.setAmount(amount);
        record.setCurrency(asset.getCurrency() != null ? asset.getCurrency() : currencyResolver.defaultForCurrentTenant());
        record.setServiceName(service);
        record.setOrganisation(org);
        costRepo.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CloudCostRecordDto> listCosts(UUID assetId, Pageable pageable) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cloud asset not found: " + assetId));
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("billingMonth"), Sort.Order.asc("serviceName")));
        return costRepo.findByCloudAssetAndDeletedAtIsNull(asset, newestFirst).map(CloudAssetServiceImpl::toCostDto);
    }

    private static CloudCostRecordDto toCostDto(CloudCostRecord r) {
        CloudCostRecordDto dto = new CloudCostRecordDto();
        dto.setId(r.getId());
        dto.setBillingMonth(r.getBillingMonth() != null ? YearMonth.from(r.getBillingMonth()).toString() : null);
        dto.setAmount(r.getAmount());
        dto.setCurrency(r.getCurrency());
        dto.setServiceName(r.getServiceName());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setUpdatedAt(r.getUpdatedAt());
        return dto;
    }

    // ── Cloud Sync ────────────────────────────────────────────────────────────

    @Override
    public int syncFromCloud(CloudProvider provider, List<String> regions) {
        requireSyncEnabled();
        Organisation org = requireTenantOrg();
        int upserted = cloudSyncDispatcher.syncProvider(provider, org, regions);
        log.info("[CloudSync] syncFromCloud({}) complete for org {} — {} asset(s) upserted",
                provider, org.getId(), upserted);
        return upserted;
    }

    @Override
    public int syncAll(List<String> regions) {
        requireSyncEnabled();
        Organisation org = requireTenantOrg();
        int upserted = cloudSyncDispatcher.syncAll(org, regions);
        log.info("[CloudSync] syncAll complete for org {} — {} total asset(s) upserted",
                org.getId(), upserted);
        return upserted;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void requireSyncEnabled() {
        if (!syncEnabled) {
            throw new com.assetiq.services.FeatureDisabledException("cloud-sync", true);
        }
    }

    private void mapToEntity(CloudAssetDto dto, CloudAsset entity, Organisation org) {
        entity.setName(dto.getName());
        entity.setProvider(dto.getProvider());
        entity.setRegion(dto.getRegion());
        entity.setResourceId(dto.getResourceId());
        entity.setResourceType(dto.getResourceType());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        entity.setAccountId(dto.getAccountId());
        entity.setMonthlyCostEstimate(dto.getMonthlyCostEstimate());
        entity.setCurrency(currencyResolver.resolveOrDefault(dto.getCurrency()));
        entity.setEnvironment(CloudEnvironment.normaliseToName(dto.getEnvironment()));
        entity.setTags(dto.getTags());
        entity.setDescription(dto.getDescription());
        entity.setOrganisation(org);
    }

    private CloudAssetDto toDto(CloudAsset a) {
        CloudAssetDto dto = new CloudAssetDto();
        dto.setId(a.getId());
        dto.setName(a.getName());
        dto.setProvider(a.getProvider());
        dto.setRegion(a.getRegion());
        dto.setResourceId(a.getResourceId());
        dto.setResourceType(a.getResourceType());
        dto.setStatus(a.getStatus());
        dto.setAccountId(a.getAccountId());
        dto.setMonthlyCostEstimate(a.getMonthlyCostEstimate());
        dto.setCurrency(a.getCurrency());
        // Legacy free-text values read in the normalised form too.
        dto.setEnvironment(CloudEnvironment.normaliseToName(a.getEnvironment()));
        dto.setTags(a.getTags());
        dto.setDescription(a.getDescription());
        dto.setLastSyncAt(a.getLastSyncAt());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        return dto;
    }
}
