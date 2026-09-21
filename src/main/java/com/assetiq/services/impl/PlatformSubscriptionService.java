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
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Operator-only plan grants: pilots, demos and goodwill credits, with no payment.
 *
 * <p>The write is made with the tenant context switched to the target organisation,
 * so the ORM tenant guard still checks every row touched; the audit event is written
 * afterwards under the operator's own organisation, naming the target and the reason.
 */
@Service
@Slf4j
public class PlatformSubscriptionService {

    private final OrganisationRepository organisationRepository;
    private final OrganisationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final RbacAuditService auditService;

    public PlatformSubscriptionService(OrganisationRepository organisationRepository,
                                       OrganisationSubscriptionRepository subscriptionRepository,
                                       SubscriptionPlanRepository planRepository,
                                       RbacAuditService auditService) {
        this.organisationRepository = organisationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void grant(UUID organisationId, PlanGrantRequest request) {
        SubscriptionPlan plan = planRepository.findByCodeAndDeletedAtIsNull(request.getPlanCode())
                .filter(SubscriptionPlan::getActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive plan " + request.getPlanCode()));
        Organisation target = organisationRepository.findByIdAndDeletedAtIsNull(organisationId)
                .orElseThrow(() -> new EntityNotFoundException("Organisation not found"));

        UUID operatorOrg = TenantContext.getOrganisationId();
        String previousPlan;
        TenantContext.setOrganisationId(target.getId());
        try {
            OrganisationSubscription subscription = subscriptionRepository
                    .findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(target)
                    .orElseGet(() -> {
                        OrganisationSubscription s = new OrganisationSubscription();
                        s.setOrganisation(target);
                        return s;
                    });
            previousPlan = subscription.getPlan() == null ? null : subscription.getPlan().getCode();
            Instant now = Instant.now();
            subscription.setPlan(plan);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now.plus(request.getDays(), ChronoUnit.DAYS));
            subscription.setNextBillingAt(null);
            // Nothing will charge for a grant, so it must end rather than "renew".
            subscription.setAutoRenew(false);
            subscription.setCanceledAt(null);
            subscription.setPastDueSince(null);
            subscription.setScheduledPlan(null);
            subscription.setScheduledChangeAt(null);
            subscriptionRepository.saveAndFlush(subscription);
        } finally {
            TenantContext.setOrganisationId(operatorOrg);
        }

        auditService.recordPlanGranted(target.getId(), previousPlan,
                plan.getCode() + " for " + request.getDays() + " days: " + request.getReason());
        log.warn("[BILLING] Platform grant: org {} {} -> {} for {} days ({})",
                target.getId(), previousPlan, plan.getCode(), request.getDays(), request.getReason());
    }
}
