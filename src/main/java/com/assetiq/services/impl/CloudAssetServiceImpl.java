package com.assetiq.services.impl;

import com.assetiq.cloudsync.CloudSyncDispatcher;
import com.assetiq.dto.CloudAssetDto;
import com.assetiq.dto.CloudCostSummaryDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    public CloudAssetServiceImpl(OrganisationRepository organisationRepository,
                                 CloudAssetRepository cloudAssetRepo,
                                 CloudCostRecordRepository costRepo,
                                 CurrencyResolver currencyResolver,
                                 CloudSyncDispatcher cloudSyncDispatcher) {
        super(organisationRepository);
        this.cloudAssetRepo = cloudAssetRepo;
        this.costRepo = costRepo;
        this.currencyResolver = currencyResolver;
        this.cloudSyncDispatcher = cloudSyncDispatcher;
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
                        || environment.equalsIgnoreCase(a.getEnvironment()))
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
                .orElseThrow(() -> new NoSuchElementException("Cloud asset not found: " + id));
        return toDto(asset);
    }

    @Override
    public CloudAssetDto update(UUID id, CloudAssetDto dto) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Cloud asset not found: " + id));
        mapToEntity(dto, asset, org);
        asset.setLastSyncAt(Instant.now());
        return toDto(cloudAssetRepo.save(asset));
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new NoSuchElementException("Cloud asset not found: " + id));
        asset.setDeletedAt(Instant.now());
        cloudAssetRepo.save(asset);
    }

    @Override
    @Transactional(readOnly = true)
    public CloudCostSummaryDto getCostSummary() {
        Organisation org = requireTenantOrg();
        List<CloudAsset> assets = cloudAssetRepo.findByOrganisationAndDeletedAtIsNull(org);

        BigDecimal total = assets.stream()
                .filter(a -> a.getMonthlyCostEstimate() != null)
                .map(CloudAsset::getMonthlyCostEstimate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<CloudProvider, BigDecimal> byProvider = assets.stream()
                .filter(a -> a.getMonthlyCostEstimate() != null)
                .collect(Collectors.groupingBy(
                        CloudAsset::getProvider,
                        Collectors.reducing(BigDecimal.ZERO, CloudAsset::getMonthlyCostEstimate, BigDecimal::add)));

        Map<String, BigDecimal> byEnv = assets.stream()
                .filter(a -> a.getMonthlyCostEstimate() != null && a.getEnvironment() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getEnvironment().toUpperCase(),
                        Collectors.reducing(BigDecimal.ZERO, CloudAsset::getMonthlyCostEstimate, BigDecimal::add)));

        List<CloudAsset> top5 = cloudAssetRepo.findTopByOrganisationOrderByCost(org, PageRequest.of(0, 5));
        List<CloudCostSummaryDto.CloudAssetCostEntry> topAssets = top5.stream().map(a -> {
            CloudCostSummaryDto.CloudAssetCostEntry e = new CloudCostSummaryDto.CloudAssetCostEntry();
            e.setAssetName(a.getName());
            e.setResourceType(a.getResourceType().name());
            e.setMonthlyCost(a.getMonthlyCostEstimate());
            return e;
        }).collect(Collectors.toList());

        String currency = assets.stream().map(CloudAsset::getCurrency).filter(Objects::nonNull)
                .findFirst().orElseGet(currencyResolver::defaultForCurrentTenant);

        CloudCostSummaryDto summary = new CloudCostSummaryDto();
        summary.setTotalMonthlyCost(total);
        summary.setCurrency(currency);
        summary.setCostByProvider(byProvider);
        summary.setCostByEnvironment(byEnv);
        summary.setTopAssets(topAssets);
        return summary;
    }

    @Override
    public void recordMonthlyCost(UUID assetId, String billingMonth, BigDecimal amount, String serviceName) {
        Organisation org = requireTenantOrg();
        CloudAsset asset = cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new NoSuchElementException("Cloud asset not found: " + assetId));

        LocalDate month = LocalDate.parse(billingMonth + "-01");

        CloudCostRecord record = new CloudCostRecord();
        record.setCloudAsset(asset);
        record.setBillingMonth(month);
        record.setAmount(amount);
        record.setCurrency(asset.getCurrency() != null ? asset.getCurrency() : currencyResolver.defaultForCurrentTenant());
        record.setServiceName(serviceName);
        record.setOrganisation(org);
        costRepo.save(record);
    }

    // ── Cloud Sync ────────────────────────────────────────────────────────────

    @Override
    public int syncFromCloud(CloudProvider provider, List<String> regions) {
        Organisation org = requireTenantOrg();
        int upserted = cloudSyncDispatcher.syncProvider(provider, org, regions);
        log.info("[CloudSync] syncFromCloud({}) complete for org {} — {} asset(s) upserted",
                provider, org.getId(), upserted);
        return upserted;
    }

    @Override
    public int syncAll(List<String> regions) {
        Organisation org = requireTenantOrg();
        int upserted = cloudSyncDispatcher.syncAll(org, regions);
        log.info("[CloudSync] syncAll complete for org {} — {} total asset(s) upserted",
                org.getId(), upserted);
        return upserted;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
        entity.setEnvironment(dto.getEnvironment());
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
        dto.setEnvironment(a.getEnvironment());
        dto.setTags(a.getTags());
        dto.setDescription(a.getDescription());
        dto.setLastSyncAt(a.getLastSyncAt());
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getUpdatedAt());
        return dto;
    }
}
