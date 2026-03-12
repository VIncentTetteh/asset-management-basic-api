package com.example.demo.services.impl;

import com.example.demo.dto.SoftwareLicenseDto;
import com.example.demo.enums.LicenseStatus;
import com.example.demo.models.Organisation;
import com.example.demo.models.SoftwareLicense;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.SoftwareLicenseRepository;
import com.example.demo.services.SoftwareLicenseService;
import com.example.demo.services.TenantAwareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SoftwareLicenseServiceImpl extends TenantAwareService implements SoftwareLicenseService {

    private final SoftwareLicenseRepository licenseRepository;
    private final AssetRepository assetRepository;

    public SoftwareLicenseServiceImpl(SoftwareLicenseRepository licenseRepository,
                                      AssetRepository assetRepository,
                                      OrganisationRepository organisationRepository) {
        super(organisationRepository);
        this.licenseRepository = licenseRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    public SoftwareLicenseDto create(SoftwareLicenseDto dto) {
        Organisation org = requireTenantOrg();

        if (dto.getLicenseKey() != null && !dto.getLicenseKey().isBlank()) {
            if (licenseRepository.existsByLicenseKeyAndOrganisationIdAndDeletedAtIsNull(
                    dto.getLicenseKey(), org.getId())) {
                throw new IllegalArgumentException("A license with this key already exists in your organisation");
            }
        }

        SoftwareLicense license = new SoftwareLicense();
        applyDto(license, dto, org);
        license.setOrganisation(org);
        return mapToDto(licenseRepository.save(license));
    }

    @Override
    @Transactional(readOnly = true)
    public SoftwareLicenseDto getById(UUID id) {
        Organisation org = requireTenantOrg();
        return mapToDto(findOrThrow(id, org));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SoftwareLicenseDto> listAll() {
        Organisation org = requireTenantOrg();
        return licenseRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SoftwareLicenseDto> listExpiringSoon(int days) {
        Organisation org = requireTenantOrg();
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return licenseRepository.findExpiringSoon(org, cutoff)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SoftwareLicenseDto> listOverAllocated() {
        Organisation org = requireTenantOrg();
        return licenseRepository.findOverAllocated(org)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public SoftwareLicenseDto update(UUID id, SoftwareLicenseDto dto) {
        Organisation org = requireTenantOrg();
        SoftwareLicense license = findOrThrow(id, org);
        applyDto(license, dto, org);
        return mapToDto(licenseRepository.save(license));
    }

    @Override
    public SoftwareLicenseDto patch(UUID id, SoftwareLicenseDto dto) {
        Organisation org = requireTenantOrg();
        SoftwareLicense license = findOrThrow(id, org);
        if (dto.getName() != null) license.setName(dto.getName());
        if (dto.getVendor() != null) license.setVendor(dto.getVendor());
        if (dto.getLicenseKey() != null) license.setLicenseKey(dto.getLicenseKey());
        if (dto.getProductName() != null) license.setProductName(dto.getProductName());
        if (dto.getVersion() != null) license.setVersion(dto.getVersion());
        if (dto.getLicenseType() != null) license.setLicenseType(dto.getLicenseType());
        if (dto.getStatus() != null) license.setStatus(dto.getStatus());
        if (dto.getTotalSeats() != null) license.setTotalSeats(dto.getTotalSeats());
        if (dto.getUsedSeats() != null) license.setUsedSeats(dto.getUsedSeats());
        if (dto.getPurchaseCost() != null) license.setPurchaseCost(dto.getPurchaseCost());
        if (dto.getAnnualRenewalCost() != null) license.setAnnualRenewalCost(dto.getAnnualRenewalCost());
        if (dto.getCurrency() != null) license.setCurrency(dto.getCurrency());
        if (dto.getPurchaseDate() != null) license.setPurchaseDate(dto.getPurchaseDate());
        if (dto.getExpiryDate() != null) license.setExpiryDate(dto.getExpiryDate());
        if (dto.getRenewalDate() != null) license.setRenewalDate(dto.getRenewalDate());
        if (dto.getAutoRenew() != null) license.setAutoRenew(dto.getAutoRenew());
        if (dto.getLicenseDocumentUrl() != null) license.setLicenseDocumentUrl(dto.getLicenseDocumentUrl());
        if (dto.getNotes() != null) license.setNotes(dto.getNotes());
        if (dto.getAssetId() != null) {
            assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(
                    dto.getAssetId(), org).ifPresent(license::setAsset);
        }
        return mapToDto(licenseRepository.save(license));
    }

    @Override
    public void delete(UUID id) {
        Organisation org = requireTenantOrg();
        SoftwareLicense license = findOrThrow(id, org);
        license.setDeletedAt(Instant.now());
        licenseRepository.save(license);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUtilizationSummary() {
        Organisation org = requireTenantOrg();
        List<SoftwareLicense> licenses = licenseRepository.findByOrganisationAndDeletedAtIsNull(org);

        int totalSeats = licenses.stream()
                .filter(l -> l.getTotalSeats() != null)
                .mapToInt(SoftwareLicense::getTotalSeats).sum();
        int usedSeats = licenses.stream()
                .filter(l -> l.getUsedSeats() != null)
                .mapToInt(SoftwareLicense::getUsedSeats).sum();
        long expiringSoon = licenses.stream()
                .filter(l -> l.getExpiryDate() != null
                        && l.getExpiryDate().isBefore(LocalDate.now().plusDays(30)))
                .count();
        long overAllocated = licenses.stream()
                .filter(l -> l.getTotalSeats() != null && l.getUsedSeats() != null
                        && l.getUsedSeats() >= l.getTotalSeats())
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalLicenses", licenses.size());
        summary.put("activeLicenses", licenses.stream()
                .filter(l -> l.getStatus() == LicenseStatus.ACTIVE).count());
        summary.put("totalSeats", totalSeats);
        summary.put("usedSeats", usedSeats);
        summary.put("availableSeats", totalSeats - usedSeats);
        summary.put("utilizationPct", totalSeats > 0
                ? Math.round((usedSeats * 100.0) / totalSeats) : 0);
        summary.put("expiringSoon30Days", expiringSoon);
        summary.put("overAllocated", overAllocated);
        return summary;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SoftwareLicense findOrThrow(UUID id, Organisation org) {
        return licenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("License not found in your organisation"));
    }

    private void applyDto(SoftwareLicense license, SoftwareLicenseDto dto, Organisation org) {
        license.setName(dto.getName());
        license.setVendor(dto.getVendor());
        license.setLicenseKey(dto.getLicenseKey());
        license.setProductName(dto.getProductName());
        license.setVersion(dto.getVersion());
        license.setLicenseType(dto.getLicenseType());
        if (dto.getStatus() != null) license.setStatus(dto.getStatus());
        license.setTotalSeats(dto.getTotalSeats());
        if (dto.getUsedSeats() != null) license.setUsedSeats(dto.getUsedSeats());
        license.setPurchaseCost(dto.getPurchaseCost());
        license.setAnnualRenewalCost(dto.getAnnualRenewalCost());
        if (dto.getCurrency() != null) license.setCurrency(dto.getCurrency());
        license.setPurchaseDate(dto.getPurchaseDate());
        license.setExpiryDate(dto.getExpiryDate());
        license.setRenewalDate(dto.getRenewalDate());
        if (dto.getAutoRenew() != null) license.setAutoRenew(dto.getAutoRenew());
        license.setLicenseDocumentUrl(dto.getLicenseDocumentUrl());
        license.setNotes(dto.getNotes());
        if (dto.getAssetId() != null) {
            assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getAssetId(), org)
                    .ifPresent(license::setAsset);
        }
    }

    private SoftwareLicenseDto mapToDto(SoftwareLicense l) {
        SoftwareLicenseDto dto = new SoftwareLicenseDto();
        dto.setId(l.getId());
        dto.setName(l.getName());
        dto.setVendor(l.getVendor());
        dto.setLicenseKey(l.getLicenseKey());
        dto.setProductName(l.getProductName());
        dto.setVersion(l.getVersion());
        dto.setLicenseType(l.getLicenseType());
        dto.setStatus(l.getStatus());
        dto.setTotalSeats(l.getTotalSeats());
        dto.setUsedSeats(l.getUsedSeats());
        dto.setPurchaseCost(l.getPurchaseCost());
        dto.setAnnualRenewalCost(l.getAnnualRenewalCost());
        dto.setCurrency(l.getCurrency());
        dto.setPurchaseDate(l.getPurchaseDate());
        dto.setExpiryDate(l.getExpiryDate());
        dto.setRenewalDate(l.getRenewalDate());
        dto.setAutoRenew(l.getAutoRenew());
        dto.setLicenseDocumentUrl(l.getLicenseDocumentUrl());
        dto.setNotes(l.getNotes());
        dto.setOrganisationId(l.getOrganisation().getId());
        if (l.getAsset() != null) dto.setAssetId(l.getAsset().getId());
        // Computed
        if (l.getTotalSeats() != null && l.getUsedSeats() != null) {
            dto.setAvailableSeats(l.getTotalSeats() - l.getUsedSeats());
        }
        if (l.getExpiryDate() != null) {
            dto.setDaysUntilExpiry(ChronoUnit.DAYS.between(LocalDate.now(), l.getExpiryDate()));
        }
        return dto;
    }
}
