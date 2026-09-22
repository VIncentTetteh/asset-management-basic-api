package com.assetiq.services.impl;

import com.assetiq.dto.OrganisationStorageConfigDto;
import com.assetiq.exceptions.FieldValidationException;
import com.assetiq.dto.OrganisationStorageConfigResponse;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationStorageConfig;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationStorageConfigRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganisationStorageConfigServiceImplTest {

    @Mock OrganisationStorageConfigRepository configRepository;
    @Mock OrganisationRepository organisationRepository;

    private OrganisationStorageConfigServiceImpl service;
    private Organisation org;
    private OrganisationStorageConfig config;

    @BeforeEach
    void setUp() {
        service = new OrganisationStorageConfigServiceImpl(configRepository, organisationRepository);
        service.setGlobalBucket("assetiq-global");
        org = new Organisation();
        org.setId(UUID.randomUUID());
        config = new OrganisationStorageConfig();
        config.setOrganisation(org);
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(configRepository.findByOrganisationIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(config));
        when(configRepository.save(any(OrganisationStorageConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void withoutAnOverrideTheDefaultIsReportedSeparately() {
        OrganisationStorageConfigResponse r = service.get(org.getId());

        assertThat(r.getBucketOverride()).isNull();
        assertThat(r.getDefaultBucket()).isEqualTo("assetiq-global");
        assertThat(r.getBucketName()).isEqualTo("assetiq-global");
    }

    @Test
    void aBlankBucketClearsTheOverride() {
        config.setBucketName("org-bucket");
        OrganisationStorageConfigDto dto = new OrganisationStorageConfigDto();
        dto.setBucketName("  ");

        OrganisationStorageConfigResponse r = service.upsert(org.getId(), dto);

        assertThat(config.getBucketName()).isNull();
        assertThat(r.getBucketOverride()).isNull();
    }

    @Test
    void aBucketOffTheAllowListIsRefusedWithAFieldError() {
        // The server writes with its own credentials: without the allow-list an
        // admin could aim the platform at any bucket those credentials can reach.
        OrganisationStorageConfigDto dto = new OrganisationStorageConfigDto();
        dto.setBucketName("someone-elses-bucket");

        assertThatThrownBy(() -> service.upsert(org.getId(), dto))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("not an allowed storage bucket");
        assertThat(((FieldValidationException) catchThrowable(() -> service.upsert(org.getId(), dto))).getField())
                .isEqualTo("bucketName");
        assertThat(config.getBucketName()).isNull();
    }

    @Test
    void thePlatformBucketIsAlwaysAllowedAndTheListAddsToIt() {
        service.setAllowedBuckets(java.util.List.of("assetiq-eu", " AssetIQ-Archive "));

        OrganisationStorageConfigDto platform = new OrganisationStorageConfigDto();
        platform.setBucketName("assetiq-global");
        assertThatCode(() -> service.upsert(org.getId(), platform)).doesNotThrowAnyException();

        OrganisationStorageConfigDto listed = new OrganisationStorageConfigDto();
        listed.setBucketName("assetiq-eu");
        assertThatCode(() -> service.upsert(org.getId(), listed)).doesNotThrowAnyException();
        assertThat(config.getBucketName()).isEqualTo("assetiq-eu");

        // Matching is case-insensitive and ignores the spaces around a list entry.
        OrganisationStorageConfigDto spaced = new OrganisationStorageConfigDto();
        spaced.setBucketName("assetiq-archive");
        assertThatCode(() -> service.upsert(org.getId(), spaced)).doesNotThrowAnyException();

        OrganisationStorageConfigDto other = new OrganisationStorageConfigDto();
        other.setBucketName("assetiq-us");
        assertThatThrownBy(() -> service.upsert(org.getId(), other))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void clearingTheOverrideIsAlwaysAllowed() {
        config.setBucketName("assetiq-global");
        OrganisationStorageConfigDto dto = new OrganisationStorageConfigDto();
        dto.setBucketName("");

        assertThatCode(() -> service.upsert(org.getId(), dto)).doesNotThrowAnyException();
        assertThat(config.getBucketName()).isNull();
    }

    @Test
    void presignTtlIsCappedAt720Minutes() {
        try (ValidatorFactory f = Validation.buildDefaultValidatorFactory()) {
            Validator v = f.getValidator();
            OrganisationStorageConfigDto dto = new OrganisationStorageConfigDto();
            dto.setPresignMinutes(721);
            assertThat(v.validate(dto)).extracting(c -> c.getPropertyPath().toString()).containsExactly("presignMinutes");
            dto.setPresignMinutes(720);
            dto.setBucketName("Not_A_Bucket");
            assertThat(v.validate(dto)).extracting(c -> c.getPropertyPath().toString()).containsExactly("bucketName");
            dto.setBucketName("");
            assertThat(v.validate(dto)).isEmpty();
        }
    }
}
