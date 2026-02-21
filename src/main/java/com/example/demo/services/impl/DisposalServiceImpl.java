package com.example.demo.services.impl;

import com.example.demo.dto.DisposalRecordDto;
import com.example.demo.models.DisposalRecord;
import com.example.demo.models.Asset;
import com.example.demo.models.Organisation;
import com.example.demo.models.User;
import com.example.demo.enums.AssetStatus;
import com.example.demo.repositories.*;
import com.example.demo.services.DisposalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DisposalServiceImpl implements DisposalService {

    private final DisposalRecordRepository disposalRepository;
    private final AssetRepository assetRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;

    public DisposalServiceImpl(DisposalRecordRepository disposalRepository,
                             AssetRepository assetRepository,
                             OrganisationRepository organisationRepository,
                             UserRepository userRepository) {
        this.disposalRepository = disposalRepository;
        this.assetRepository = assetRepository;
        this.organisationRepository = organisationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public DisposalRecordDto createDisposalRecord(DisposalRecordDto recordDto) {
        Asset asset = assetRepository.findById(recordDto.getAssetId())
            .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        Organisation organisation = organisationRepository.findById(recordDto.getOrganisationId())
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        User approver = userRepository.findById(recordDto.getApprovedById())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DisposalRecord record = new DisposalRecord();
        record.setAsset(asset);
        record.setDisposalMethod(recordDto.getDisposalMethod());
        record.setDisposalDate(recordDto.getDisposalDate());
        record.setSaleValue(recordDto.getSaleValue());
        record.setApprovedBy(approver);
        record.setReason(recordDto.getReason());
        record.setComplianceDocumentUrl(recordDto.getComplianceDocumentUrl());
        record.setOrganisation(organisation);

        // Update asset status to disposed
        asset.setStatus(AssetStatus.DISPOSED);
        assetRepository.save(asset);

        DisposalRecord savedRecord = disposalRepository.save(record);
        return mapToDto(savedRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public DisposalRecordDto getDisposalById(UUID id) {
        DisposalRecord record = disposalRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Disposal record not found"));
        return mapToDto(record);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByAsset(UUID assetId) {
        return disposalRepository.findByAssetId(assetId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByOrganisation(UUID organisationId) {
        return disposalRepository.findByOrganisationId(organisationId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return disposalRepository.findByDisposalDateBetween(startDate, endDate).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<DisposalRecordDto> getDisposalsByApprover(UUID userId) {
        return disposalRepository.findByApprovedById(userId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toSet());
    }

    @Override
    public DisposalRecordDto updateDisposalRecord(UUID id, DisposalRecordDto recordDto) {
        DisposalRecord record = disposalRepository.findById(id)
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
    public void deleteDisposalRecord(UUID id) {
        disposalRepository.deleteById(id);
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

