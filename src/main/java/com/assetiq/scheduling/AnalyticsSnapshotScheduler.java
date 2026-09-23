package com.assetiq.scheduling;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.insights.AnalyticsSnapshotService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Records one row of estate history per tenant per night.
 *
 * <p><b>Why this exists.</b> Everything AssetIQ called a trend was computed
 * backwards from the present: today's assets, revalued month by month. That is
 * fine for depreciation, which is a function of time, and wrong for everything
 * else — an asset added last week silently appears in last year's chart, a
 * disposal erases its own history, and the numbers a manager actually watches
 * (how much is idle, how much is overdue, how many licence seats are in use)
 * cannot be reconstructed from the present at all. This job writes down what
 * was true, so a trend can be a record rather than an inference.
 *
 * <p><b>Bounded and resumable.</b> At most {@code app.analytics.snapshot.max-organisations}
 * tenants per run; each tenant is captured in its own transaction, so one
 * tenant's bad data cannot roll back the rows already written. A run that stops
 * early leaves the remaining tenants for the next night rather than failing the
 * lot.
 *
 * <p><b>Safe to run twice.</b> One snapshot per tenant per day is enforced by a
 * unique index (V63); a second run on the same day writes nothing.
 *
 * <p><b>Logs carry counts only</b> — never tenant content.
 */
@Component
public class AnalyticsSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSnapshotScheduler.class);

    private final OrganisationRepository organisationRepository;
    private final AnalyticsSnapshotService snapshotService;

    /** Tenants captured per run, so the worst case stays bounded as we grow. */
    @Value("${app.analytics.snapshot.max-organisations:1000}")
    private int maxOrganisations;

    public AnalyticsSnapshotScheduler(OrganisationRepository organisationRepository,
                                      AnalyticsSnapshotService snapshotService) {
        this.organisationRepository = organisationRepository;
        this.snapshotService = snapshotService;
    }

    /**
     * Daily at 01:40 UTC by default — before the insight refresh at 02:15, so a
     * snapshot describes the day that has just ended rather than the effects of
     * the night's own jobs. Override with {@code app.analytics.snapshot.cron};
     * set it to {@code -} to disable.
     */
    @Scheduled(cron = "${app.analytics.snapshot.cron:0 40 1 * * *}", zone = "UTC")
    @SchedulerLock(name = "analyticsSnapshot", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void captureAllTenants() {
        captureAllTenants(LocalDate.now());
    }

    /** Visible for testing: capture every tenant as of {@code on}. */
    public void captureAllTenants(LocalDate on) {
        List<Organisation> organisations = organisationRepository.findAllByDeletedAtIsNull();

        int considered = 0;
        int written = 0;
        int alreadyPresent = 0;
        int failed = 0;

        for (Organisation org : organisations) {
            if (considered >= maxOrganisations) {
                log.warn("[AnalyticsSnapshot] Stopped after {} organisations ({} remain for the next run)",
                        considered, organisations.size() - considered);
                break;
            }
            considered++;
            try {
                if (captureOne(org, on)) {
                    written++;
                } else {
                    alreadyPresent++;
                }
            } catch (DataIntegrityViolationException raced) {
                // Another instance captured this tenant between the existence check
                // and the insert. Its row is computed from the same data as ours.
                alreadyPresent++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("[AnalyticsSnapshot] Skipped organisation {}: {}", org.getId(), e.toString());
            }
        }

        if (failed > 0) {
            log.warn("[AnalyticsSnapshot] Partial run for {}: {} written, {} already present, {} failed",
                    on, written, alreadyPresent, failed);
        } else {
            log.info("[AnalyticsSnapshot] {}: {} written, {} already present", on, written, alreadyPresent);
        }
    }

    /**
     * One tenant, one transaction. {@code TenantContext} is set for the duration
     * because {@code BaseEntity} enforces the tenant boundary on every load and
     * persist, and cleared in a finally so a failure cannot leak one tenant's id
     * into the next iteration — the scheduler thread is pooled and reused.
     */
    boolean captureOne(Organisation org, LocalDate on) {
        TenantContext.setOrganisationId(org.getId());
        try {
            return snapshotService.capture(org, on).isPresent();
        } finally {
            TenantContext.clear();
        }
    }

    void configure(int maxOrganisations) {
        this.maxOrganisations = maxOrganisations;
    }
}
