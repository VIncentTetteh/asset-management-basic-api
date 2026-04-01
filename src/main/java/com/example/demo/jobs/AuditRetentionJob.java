package com.example.demo.jobs;

import com.example.demo.enums.SubscriptionStatus;
import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import com.example.demo.models.OrganisationSubscription;
import com.example.demo.models.SubscriptionPlan;
import com.example.demo.repositories.AuditEventRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.OrganisationSubscriptionRepository;
import com.example.demo.repositories.SubscriptionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job that enforces per-plan audit retention by soft-deleting old API audit events.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditEventRepository auditEventRepository;
    private final OrganisationRepository organisationRepository;
    private final OrganisationSubscriptionRepository organisationSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public AuditRetentionJob(
            AuditEventRepository auditEventRepository,
            OrganisationRepository organisationRepository,
            OrganisationSubscriptionRepository organisationSubscriptionRepository,
            SubscriptionPlanRepository subscriptionPlanRepository) {
        this.auditEventRepository = auditEventRepository;
        this.organisationRepository = organisationRepository;
        this.organisationSubscriptionRepository = organisationSubscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    // Runs daily at 02:00 UTC.
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        for (Organisation org : organisationRepository.findAllByDeletedAtIsNull()) {
            Integer retentionDays = resolveAuditRetentionDays(org);
            if (retentionDays == null || retentionDays <= 0) {
                continue;
            }

            Instant cutoff = now.minus(retentionDays.longValue(), ChronoUnit.DAYS);
            int updated = auditEventRepository.softDeleteByOrganisationCreatedAtBeforeAndDeletedAtIsNull(
                    org, cutoff, now);
            if (updated > 0) {
                log.info("[AuditRetention] Soft-deleted {} audit event(s) for org {} older than {} days",
                        updated, org.getId(), retentionDays);
            }
        }
    }

    private Integer resolveAuditRetentionDays(Organisation org) {
        OrganisationSubscription subscription = organisationSubscriptionRepository
                .findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(org)
                .orElse(null);
        if (subscription != null
                && subscription.getPlan() != null
                && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return subscription.getPlan().getAuditRetentionDays();
        }
        return subscriptionPlanRepository.findByCodeAndDeletedAtIsNull("FREEMIUM")
                .map(SubscriptionPlan::getAuditRetentionDays)
                .orElse(null);
    }
}

