package com.assetiq.services.impl;

import com.assetiq.dto.PlanGrantRequest;
import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.OrganisationSubscriptionRepository;
import com.assetiq.repositories.SubscriptionPlanRepository;
import com.assetiq.security.RbacAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformSubscriptionService")
class PlatformSubscriptionServiceTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock OrganisationSubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock RbacAuditService auditService;

    PlatformSubscriptionService service;
    UUID operatorOrg = UUID.randomUUID();
    Organisation target;
    SubscriptionPlan business;

    @BeforeEach
    void setUp() {
        service = new PlatformSubscriptionService(organisationRepository, subscriptionRepository, planRepository, auditService);
        TenantContext.setOrganisationId(operatorOrg);
        target = new Organisation();
        target.setName("Acme Ghana Ltd");
        business = new SubscriptionPlan();
        business.setCode("BUSINESS");
        business.setActive(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PlanGrantRequest request(int days) {
        PlanGrantRequest r = new PlanGrantRequest();
        r.setPlanCode("BUSINESS");
        r.setDays(days);
        r.setReason("Sales demo");
        return r;
    }

    @Test
    void grantsTheTimeBoxedPlanInsideTheTargetTenantAndAudits() {
        OrganisationSubscription existing = new OrganisationSubscription();
        existing.setOrganisation(target);
        existing.setAutoRenew(true);
        existing.setStatus(SubscriptionStatus.PAST_DUE);
        when(planRepository.findByCodeAndDeletedAtIsNull("BUSINESS")).thenReturn(Optional.of(business));
        when(organisationRepository.findByIdAndDeletedAtIsNull(target.getId())).thenReturn(Optional.of(target));
        when(subscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(target))
                .thenReturn(Optional.of(existing));
        AtomicReference<UUID> tenantDuringWrite = new AtomicReference<>();
        when(subscriptionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            tenantDuringWrite.set(TenantContext.getOrganisationId());
            return inv.getArgument(0);
        });

        service.grant(target.getId(), request(90));

        assertThat(tenantDuringWrite.get()).isEqualTo(target.getId());
        assertThat(TenantContext.getOrganisationId()).isEqualTo(operatorOrg);
        assertThat(existing.getPlan()).isEqualTo(business);
        assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(existing.getAutoRenew()).isFalse();
        assertThat(existing.getCurrentPeriodEnd())
                .isBetween(Instant.now().plus(89, ChronoUnit.DAYS), Instant.now().plus(91, ChronoUnit.DAYS));
        verify(auditService).recordPlanGranted(eq(target.getId()), any(), contains("Sales demo"));
    }

    @Test
    void restoresTheOperatorTenantEvenWhenTheWriteFails() {
        when(planRepository.findByCodeAndDeletedAtIsNull("BUSINESS")).thenReturn(Optional.of(business));
        when(organisationRepository.findByIdAndDeletedAtIsNull(target.getId())).thenReturn(Optional.of(target));
        when(subscriptionRepository.findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(target))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.grant(target.getId(), request(30))).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.getOrganisationId()).isEqualTo(operatorOrg);
        verify(auditService, never()).recordPlanGranted(any(), any(), any());
    }

    @Test
    void refusesAnUnknownPlan() {
        when(planRepository.findByCodeAndDeletedAtIsNull("BUSINESS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(target.getId(), request(30))).isInstanceOf(IllegalArgumentException.class);
    }
}
