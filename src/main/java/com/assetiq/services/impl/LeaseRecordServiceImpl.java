package com.assetiq.services.impl;

import com.assetiq.dto.LeaseRecordDto;
import com.assetiq.enums.LeaseStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.LeaseRecordService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeaseRecordServiceImpl extends TenantAwareService implements LeaseRecordService {

    private static final Logger log = LoggerFactory.getLogger(LeaseRecordServiceImpl.class);

    private final LeaseRecordRepository leaseRecordRepository;
    private final AssetRepository assetRepository;
    private final SupplierRepository supplierRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;
    private final CurrencyResolver currencyResolver;

    public LeaseRecordServiceImpl(LeaseRecordRepository leaseRecordRepository,
                                  AssetRepository assetRepository,
                                  SupplierRepository supplierRepository,
                                  DepartmentRepository departmentRepository,
                                  OrganisationRepository organisationRepository,
                                  NotificationService notificationService,
                                  CurrencyResolver currencyResolver) {
        super(organisationRepository);
        this.leaseRecordRepository = leaseRecordRepository;
        this.assetRepository = assetRepository;
        this.supplierRepository = supplierRepository;
        this.departmentRepository = departmentRepository;
        this.notificationService = notificationService;
        this.currencyResolver = currencyResolver;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public LeaseRecordDto create(LeaseRecordDto dto) {
        Organisation org = requireTenantOrg();

        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getAssetId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + dto.getAssetId()));

        Supplier lessor = supplierRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getLessorId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Supplier (lessor) not found: " + dto.getLessorId()));

        LeaseRecord record = new LeaseRecord();
        record.setAsset(asset);
        record.setLessor(lessor);
        record.setStartDate(dto.getStartDate());
        record.setEndDate(dto.getEndDate());
        record.setMonthlyPayment(dto.getMonthlyPayment());
        record.setCurrency(currencyResolver.resolveOrDefault(dto.getCurrency()));
        record.setAutoRenew(dto.getAutoRenew() != null ? dto.getAutoRenew() : false);
        record.setNoticePeriodDays(dto.getNoticePeriodDays() != null ? dto.getNoticePeriodDays() : 30);
        record.setNotes(dto.getNotes());
        record.setStatus(LeaseStatus.ACTIVE);
        record.setOrganisation(org);

        if (dto.getDepartmentId() != null) {
            departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .ifPresent(record::setDepartment);
        }

        LeaseRecord saved = leaseRecordRepository.save(record);

        notificationService.notifyOrgAdmins(org, NotificationType.LEASE_EXPIRY,
                "New Lease Created",
                "Lease for asset '" + asset.getName() + "' with lessor '" + lessor.getName()
                        + "' created. End date: " + saved.getEndDate() + ".",
                saved.getId(), null);

        log.info("Lease record {} created for asset {} with lessor {}", saved.getId(), asset.getId(), lessor.getId());
        return toDto(saved);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public LeaseRecordDto update(UUID id, LeaseRecordDto dto) {
        Organisation org = requireTenantOrg();
        LeaseRecord record = requireLease(id, org);

        if (dto.getEndDate() != null) record.setEndDate(dto.getEndDate());
        if (dto.getMonthlyPayment() != null) record.setMonthlyPayment(dto.getMonthlyPayment());
        if (dto.getCurrency() != null) record.setCurrency(dto.getCurrency().toUpperCase());
        if (dto.getAutoRenew() != null) record.setAutoRenew(dto.getAutoRenew());
        if (dto.getNoticePeriodDays() != null) record.setNoticePeriodDays(dto.getNoticePeriodDays());
        if (dto.getNotes() != null) record.setNotes(dto.getNotes());
        if (dto.getStatus() != null) record.setStatus(dto.getStatus());

        if (dto.getDepartmentId() != null) {
            departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getDepartmentId(), org)
                    .ifPresent(record::setDepartment);
        }

        LeaseRecord saved = leaseRecordRepository.save(record);
        log.info("Lease record {} updated", id);
        return toDto(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public LeaseRecordDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return toDto(requireLease(id, org));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseRecordDto> listAll() {
        Organisation org = requireTenantOrg();
        return leaseRecordRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseRecordDto> listByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        return leaseRecordRepository.findByAssetAndDeletedAtIsNull(asset)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaseRecordDto> listExpiringSoon(int daysAhead) {
        Organisation org = requireTenantOrg();
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead);
        return leaseRecordRepository.findExpiringSoon(org, cutoff)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Terminate ─────────────────────────────────────────────────────────────

    @Override
    public LeaseRecordDto terminate(UUID id, String reason) {
        Organisation org = requireTenantOrg();
        LeaseRecord record = requireLease(id, org);

        if (record.getStatus() == LeaseStatus.TERMINATED) {
            throw new IllegalStateException("Lease is already terminated.");
        }
        if (record.getStatus() == LeaseStatus.EXPIRED) {
            throw new IllegalStateException("Cannot terminate an expired lease.");
        }

        record.setStatus(LeaseStatus.TERMINATED);
        if (reason != null && !reason.isBlank()) {
            record.setNotes((record.getNotes() != null ? record.getNotes() + "\n" : "")
                    + "Termination reason: " + reason);
        }

        LeaseRecord saved = leaseRecordRepository.save(record);

        notificationService.notifyOrgAdmins(org, NotificationType.LEASE_EXPIRY,
                "Lease Terminated",
                "Lease for asset '" + record.getAsset().getName() + "' has been terminated."
                        + (reason != null ? " Reason: " + reason : ""),
                saved.getId(), null);

        log.info("Lease record {} terminated. Reason: {}", id, reason);
        return toDto(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        LeaseRecord record = requireLease(id, org);
        record.setDeletedAt(Instant.now());
        leaseRecordRepository.save(record);
        log.info("Soft-deleted lease record {}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LeaseRecord requireLease(UUID id, Organisation org) {
        return leaseRecordRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Lease record not found: " + id));
    }

    private LeaseRecordDto toDto(LeaseRecord r) {
        LeaseRecordDto dto = new LeaseRecordDto();
        dto.setId(r.getId());
        dto.setAssetId(r.getAsset().getId());
        dto.setAssetName(r.getAsset().getName());
        dto.setLessorId(r.getLessor().getId());
        dto.setLessorName(r.getLessor().getName());
        dto.setStartDate(r.getStartDate());
        dto.setEndDate(r.getEndDate());
        dto.setMonthlyPayment(r.getMonthlyPayment());
        dto.setCurrency(r.getCurrency());
        dto.setAutoRenew(r.getAutoRenew());
        dto.setNoticePeriodDays(r.getNoticePeriodDays());
        dto.setNotes(r.getNotes());
        dto.setStatus(r.getStatus());
        dto.setOrganisationId(r.getOrganisation().getId());
        if (r.getDepartment() != null) {
            dto.setDepartmentId(r.getDepartment().getId());
        }
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}
