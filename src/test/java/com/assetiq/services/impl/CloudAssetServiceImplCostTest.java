package com.assetiq.services.impl;

import com.assetiq.cloudsync.CloudSyncDispatcher;
import com.assetiq.dto.CloudAssetDto;
import com.assetiq.dto.CloudCostRecordDto;
import com.assetiq.dto.CloudCostSummaryDto;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.CloudCostRecord;
import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.CloudAssetRepository;
import com.assetiq.repositories.CloudCostRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.CurrencyResolver;
import com.assetiq.services.money.MoneyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloudAssetServiceImplCostTest {

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Mock OrganisationRepository organisationRepository;
    @Mock CloudAssetRepository cloudAssetRepo;
    @Mock CloudCostRecordRepository costRepo;
    @Mock CurrencyResolver currencyResolver;
    @Mock CloudSyncDispatcher dispatcher;

    private CloudAssetServiceImpl service;
    private Organisation org;
    private CloudAsset web;
    private CloudAsset db;

    @BeforeEach
    void setUp() {
        service = new CloudAssetServiceImpl(organisationRepository, cloudAssetRepo, costRepo, currencyResolver,
                dispatcher, MoneyTestSupport.aggregatorWithRates(Map.of("USD", "15")));
        service.setClock(Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        org.setBillingCurrency("GHS");
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        web = asset("web", "100", "PROD");
        db = asset("db", "200", "DEV");
        when(cloudAssetRepo.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(web, db));
        when(costRepo.save(any(CloudCostRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CloudAsset asset(String name, String estimate, String env) {
        CloudAsset a = new CloudAsset();
        a.setId(UUID.randomUUID());
        a.setName(name);
        a.setOrganisation(org);
        a.setProvider(CloudProvider.AWS);
        a.setResourceType(CloudResourceType.VIRTUAL_MACHINE);
        a.setCurrency("USD");
        a.setEnvironment(env);
        a.setMonthlyCostEstimate(new BigDecimal(estimate));
        when(cloudAssetRepo.findByIdAndOrganisationAndDeletedAtIsNull(a.getId(), org)).thenReturn(Optional.of(a));
        return a;
    }

    private CloudCostRecord record(CloudAsset a, LocalDate month, String amount, String service) {
        CloudCostRecord r = new CloudCostRecord();
        r.setId(UUID.randomUUID());
        r.setCloudAsset(a);
        r.setBillingMonth(month);
        r.setAmount(new BigDecimal(amount));
        r.setCurrency("USD");
        r.setServiceName(service);
        return r;
    }

    @Test
    void summaryUsesThisMonthsActualsWhenRecorded() {
        when(costRepo.findByOrganisationAndBillingMonthAndDeletedAtIsNull(org, SEPTEMBER)).thenReturn(List.of(
                record(web, SEPTEMBER, "30", "EC2"), record(web, SEPTEMBER, "10", "EBS")));

        CloudCostSummaryDto s = service.getCostSummary();

        // web: actual 40 USD (not the 100 estimate); db: estimate 200 USD. At 15 GHS/USD.
        assertThat(s.getTotalMonthlyCost()).isEqualByComparingTo("3600.00");
        assertThat(s.getCostByEnvironment().get("PROD")).isEqualByComparingTo("600.00");
        assertThat(s.getActualsMonth()).isEqualTo("2026-09");
        assertThat(s.getAssetsWithActuals()).isEqualTo(1);
        assertThat(s.getTopAssets().get(0).getAssetName()).isEqualTo("db");
    }

    @Test
    void summaryFallsBackToEstimatesWithoutActuals() {
        when(costRepo.findByOrganisationAndBillingMonthAndDeletedAtIsNull(org, SEPTEMBER)).thenReturn(List.of());

        CloudCostSummaryDto s = service.getCostSummary();

        assertThat(s.getTotalMonthlyCost()).isEqualByComparingTo("4500.00");
        assertThat(s.getAssetsWithActuals()).isZero();
    }

    @Test
    void recordingTheSameMonthAndServiceAgainUpdatesIt() {
        CloudCostRecord existing = record(web, SEPTEMBER, "30", "EC2");
        when(costRepo.findFirstByCloudAssetAndBillingMonthAndServiceNameAndDeletedAtIsNull(web, SEPTEMBER, "EC2"))
                .thenReturn(Optional.of(existing));

        service.recordMonthlyCost(web.getId(), "2026-09", new BigDecimal("35"), " EC2 ");

        assertThat(existing.getAmount()).isEqualByComparingTo("35");
        verify(costRepo).save(existing);
    }

    @Test
    void blankServiceNameIsStoredAsNullAndUpsertedAsTheWholeAsset() {
        CloudCostRecord whole = record(web, SEPTEMBER, "50", null);
        when(costRepo.findFirstByCloudAssetAndBillingMonthAndServiceNameIsNullAndDeletedAtIsNull(web, SEPTEMBER))
                .thenReturn(Optional.of(whole));

        service.recordMonthlyCost(web.getId(), "2026-09", new BigDecimal("55"), "   ");

        assertThat(whole.getAmount()).isEqualByComparingTo("55");
        assertThat(whole.getServiceName()).isNull();
    }

    @Test
    void aNewMonthCreatesARecord() {
        service.recordMonthlyCost(web.getId(), "2026-08", new BigDecimal("12"), null);

        ArgumentCaptor<CloudCostRecord> saved = ArgumentCaptor.forClass(CloudCostRecord.class);
        verify(costRepo).save(saved.capture());
        assertThat(saved.getValue().getBillingMonth()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.getValue().getServiceName()).isNull();
    }

    @Test
    void costHistoryIsNewestFirstAndMapped() {
        CloudCostRecord aug = record(web, LocalDate.of(2026, 8, 1), "20", "EC2");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(costRepo.findByCloudAssetAndDeletedAtIsNull(eq(web), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(aug), inv.getArgument(1), 1));

        Page<CloudCostRecordDto> page = service.listCosts(web.getId(), PageRequest.of(0, 24));

        assertThat(page.getContent()).singleElement().satisfies(d -> {
            assertThat(d.getBillingMonth()).isEqualTo("2026-08");
            assertThat(d.getServiceName()).isEqualTo("EC2");
        });
        assertThat(pageable.getValue().getSort().getOrderFor("billingMonth").isDescending()).isTrue();
    }

    @Test
    void createNormalisesTheEnvironment() {
        when(cloudAssetRepo.save(any(CloudAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        CloudAssetDto dto = new CloudAssetDto();
        dto.setName("x");
        dto.setProvider(CloudProvider.AWS);
        dto.setRegion("eu-west-1");
        dto.setResourceId("i-9");
        dto.setResourceType(CloudResourceType.CDN);
        dto.setEnvironment("production");

        assertThat(service.create(dto).getEnvironment()).isEqualTo("PROD");

        dto.setEnvironment(null);
        assertThat(service.create(dto).getEnvironment()).isNull();
    }
}
