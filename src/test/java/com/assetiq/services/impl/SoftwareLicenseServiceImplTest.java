package com.assetiq.services.impl;

import com.assetiq.dto.SoftwareLicenseDto;
import com.assetiq.enums.LicenseType;
import com.assetiq.exceptions.FieldValidationException;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.CurrencyResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareLicenseServiceImplTest {

    @Mock SoftwareLicenseRepository licenseRepository;
    @Mock AssetRepository assetRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock CurrencyResolver currencyResolver;

    private SoftwareLicenseServiceImpl service;
    private Organisation org;
    private SoftwareLicense license;
    private Asset asset;

    @BeforeEach
    void setUp() {
        service = new SoftwareLicenseServiceImpl(licenseRepository, assetRepository, organisationRepository,
                currencyResolver);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(currencyResolver.resolveOrDefault(any())).thenReturn("GHS");
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Build server");
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org)).thenReturn(Optional.of(asset));
        license = new SoftwareLicense();
        license.setId(UUID.randomUUID());
        license.setOrganisation(org);
        license.setAsset(asset);
        when(licenseRepository.findByIdAndOrganisationAndDeletedAtIsNull(license.getId(), org))
                .thenReturn(Optional.of(license));
        when(licenseRepository.save(any(SoftwareLicense.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SoftwareLicenseDto body() {
        SoftwareLicenseDto dto = new SoftwareLicenseDto();
        dto.setName("IDE");
        dto.setVendor("JetBrains");
        dto.setLicenseType(LicenseType.values()[0]);
        dto.setPurchaseDate(LocalDate.of(2026, 1, 1));
        dto.setExpiryDate(LocalDate.of(2027, 1, 1));
        return dto;
    }

    @Test
    void expiryBeforePurchaseIsRefusedOnCreatePutAndPatch() {
        SoftwareLicenseDto dto = body();
        dto.setExpiryDate(LocalDate.of(2025, 12, 31));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("expiryDate");
        assertThatThrownBy(() -> service.update(license.getId(), dto))
                .isInstanceOf(FieldValidationException.class);

        license.setPurchaseDate(LocalDate.of(2026, 6, 1));
        SoftwareLicenseDto patch = new SoftwareLicenseDto();
        patch.setExpiryDate(LocalDate.of(2026, 5, 1));
        assertThatThrownBy(() -> service.patch(license.getId(), patch))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void putUnlinksTheAssetAndReturnsItsNameWhenLinked() {
        SoftwareLicenseDto dto = body();
        dto.setAssetId(asset.getId());
        assertThat(service.update(license.getId(), dto).getAssetName()).isEqualTo("Build server");

        dto.setAssetId(null);
        service.update(license.getId(), dto);
        assertThat(license.getAsset()).isNull();
    }

    @Test
    void unknownAssetIsAFieldError() {
        SoftwareLicenseDto dto = body();
        dto.setAssetId(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(license.getId(), dto))
                .isInstanceOf(FieldValidationException.class)
                .extracting("field").isEqualTo("assetId");
    }

    @Test
    void licenseKeyIsNeverPartOfTheDto() {
        assertThat(java.util.Arrays.stream(SoftwareLicenseDto.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("licenseKey");
    }
}
