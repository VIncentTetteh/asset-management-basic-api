package com.assetiq.services.impl;

import com.assetiq.cloudsync.CloudSyncDispatcher;
import com.assetiq.enums.CloudProvider;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.CloudAssetRepository;
import com.assetiq.repositories.CloudCostRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.FeatureDisabledException;
import com.assetiq.services.money.MoneyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudAssetServiceImplSyncTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock CloudAssetRepository cloudAssetRepo;
    @Mock CloudCostRecordRepository costRepo;
    @Mock CurrencyResolver currencyResolver;
    @Mock CloudSyncDispatcher dispatcher;
    @Mock MoneyAggregator moneyAggregator;

    private CloudAssetServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new CloudAssetServiceImpl(organisationRepository, cloudAssetRepo, costRepo, currencyResolver,
                dispatcher, moneyAggregator);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncIsRefusedUnlessEnabled() {
        assertThatThrownBy(() -> service.syncAll(null)).isInstanceOf(FeatureDisabledException.class);
        assertThatThrownBy(() -> service.syncFromCloud(CloudProvider.AWS, null))
                .isInstanceOf(FeatureDisabledException.class);
        verify(dispatcher, never()).syncAll(any(), any());
    }

    @Test
    void syncRunsWhenEnabled() {
        service.setSyncEnabled(true);
        when(dispatcher.syncAll(eq(org), isNull())).thenReturn(3);
        assertThat(service.syncAll(null)).isEqualTo(3);
    }

    @Test
    void costNeedsAValidMonthAndNonNegativeAmount() {
        CloudAsset asset = new CloudAsset();
        UUID id = UUID.randomUUID();
        when(cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(id, org)).thenReturn(Optional.of(asset));
        assertThatThrownBy(() -> service.recordMonthlyCost(id, "2026-13", BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordMonthlyCost(id, "2026-09", new BigDecimal("-1"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
