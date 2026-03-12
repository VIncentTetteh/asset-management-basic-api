package com.example.demo.services.impl;

import com.example.demo.dto.DisposalRecordDto;
import com.example.demo.models.DisposalRecord;
import com.example.demo.models.Asset;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.enums.AssetStatus;
import com.example.demo.repositories.*;
import com.example.demo.enums.NotificationType;
import com.example.demo.services.DisposalService;
import com.example.demo.services.NotificationService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DisposalServiceImpl extends TenantAwareService implements DisposalService {

    private final DisposalRecordRepository disposalRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DisposalServiceImpl(DisposalRecordRepository disposalRepository,
            AssetRepository assetRepository,
            OrganisationRepository organisationRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        super(organisationRepository);
        this.disposalRepository = disposalRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public DisposalRecordDto createDisposalRecord(DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();

        // Asset must belong to the tenant org
        Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(recordDto.getAssetId(), org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));

        // Approver must belong to the tenant org
        User approver = userRepository.findByIdAndOrganisation(recordDto.getApprovedById(), org)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found in your organisation"));

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new IllegalArgumentException("Asset has already been disposed");
        }

        DisposalRecord record = new DisposalRecord();
        record.setAsset(asset);
        record.setDisposalMethod(recordDto.getDisposalMethod());
        record.setDisposalDate(recordDto.getDisposalDate());
        record.setSaleValue(recordDto.getSaleValue());
        record.setApprovedBy(approver);
        record.setReason(recordDto.getReason());
        record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());
        record.setOrganisation(org);

        // Mark asset as disposed and release it from any assigned user
        asset.setStatus(AssetStatus.DISPOSED);
        asset.setAssignedUser(null);
        assetRepository.save(asset);

        DisposalRecord savedRecord = disposalRepository.save(record);
        notificationService.notifyOrgAdmins(org, NotificationType.DISPOSAL,
                "Asset Disposed",
                "Asset '" + asset.getName() + "' has been disposed via " + record.getDisposalMethod() + ".",
                savedRecord.getId(), "/api/v1/disposals/" + savedRecord.getId());
        return mapToDto(savedRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public DisposalRecordDto getDisposalById(UUID id) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        return mapToDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByAsset(UUID assetId) {
        Organisation org = requireTenantOrg();
        assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(assetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found in your organisation"));
        return disposalRepository.findByAssetIdAndDeletedAtIsNull(assetId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByOrganisation(UUID organisationId) {
        Organisation org = requireTenantOrg();
        // Always use tenant org, ignore the passed organisationId
        return disposalRepository.findByOrganisationAndDeletedAtIsNull(org).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByDateRange(LocalDate startDate, LocalDate endDate) {
        Organisation org = requireTenantOrg();
        return disposalRepository.findByOrganisationAndDisposalDateBetweenAndDeletedAtIsNull(org, startDate, endDate)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByApprover(UUID userId) {
        Organisation org = requireTenantOrg();
        userRepository.findByIdAndOrganisation(userId, org)
                .orElseThrow(() -> new IllegalArgumentException("User not found in your organisation"));
        return disposalRepository.findByApprovedByIdAndDeletedAtIsNull(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toSet());
    }

    @Override
    public DisposalRecordDto updateDisposalRecord(UUID id, DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));

        record.setDisposalMethod(recordDto.getDisposalMethod());
        record.setDisposalDate(recordDto.getDisposalDate());
        record.setSaleValue(recordDto.getSaleValue());
        record.setReason(recordDto.getReason());
        record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());

        DisposalRecord updatedRecord = disposalRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public DisposalRecordDto patchDisposalRecord(UUID id, DisposalRecordDto recordDto) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));

        if (recordDto.getDisposalMethod() != null) {
            record.setDisposalMethod(recordDto.getDisposalMethod());
        }
        if (recordDto.getDisposalDate() != null) {
            record.setDisposalDate(recordDto.getDisposalDate());
        }
        if (recordDto.getSaleValue() != null) {
            record.setSaleValue(recordDto.getSaleValue());
        }
        if (recordDto.getReason() != null) {
            record.setReason(recordDto.getReason());
        }
        if (recordDto.getComplianceDocumentUrl() != null) {
            record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());
        }

        DisposalRecord updatedRecord = disposalRepository.save(record);
        return mapToDto(updatedRecord);
    }

    @Override
    public void deleteDisposalRecord(UUID id) {
        Organisation org = requireTenantOrg();
        DisposalRecord record = disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        record.setDeletedAt(Instant.now());
        disposalRepository.save(record);
    }

    private DisposalRecordDto mapToDto(DisposalRecord record) {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setId(record.getId());
        dto.setAssetId(record.getAsset().getId());
        dto.setDisposalMethod(record.getDisposalMethod());
        dto.setDisposalDate(record.getDisposalDate());
        dto.setSaleValue(record.getSaleValue());
        dto.setApprovedById(record.getApprovedBy().getId());
        dto.setReason(record.getReason());
        dto.setComplianceDocumentUrl(record.getComplianceDocumentUrl());
        dto.setOrganisationId(record.getOrganisation().getId());
        return dto;
    }
}
