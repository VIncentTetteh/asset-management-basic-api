package com.assetiq.scheduling;

import com.assetiq.models.Organisation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.PredictiveMaintenanceService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refreshes predictive insights for every opted-in tenant, nightly.
 *
 * <p><b>Why this exists.</b> The six maintenance heuristics were only ever
 * reachable from {@code POST /api/v1/ai/insights/generate} — a button. Rules
 * that run when somebody happens to press a button are, in practice, rules that
 * do not run: the dashboard showed nothing, and the assistant had no insights to
 * cite. This makes them a standing property of the data rather than an action.
 *
 * <p><b>One tenant's failure does not stop the rest.</b> Each organisation is
 * refreshed in its own transaction and its own try/catch. A tenant with corrupt
 * data, a missing currency or a concurrent refresh is logged and skipped, and
 * the run continues. The closing log line always reports refreshed / skipped /
 * failed counts, so a partial run is visible rather than silent.
 *
 * <p><b>Bounded.</b> At most {@code app.insights.refresh.max-assets-per-organisation}
 * assets are analysed per tenant per run, least-recently-updated first, so one
 * large tenant cannot consume the window and starve the others. ShedLock's
 * {@code lockAtMostFor} is the outer backstop.
 *
 * <p><b>Safe to run twice.</b> Insights are keyed one-per-asset-per-type by the
 * partial unique index added in V61. If this job collides with someone pressing
 * Regenerate, the loser's transaction for that one tenant rolls back and is
 * counted as a conflict — the winner's insights are computed from the same rows
 * and are equally correct, so there is nothing to retry.
 *
 * <p><b>Logs carry counts only</b> — organisation ids and totals, never an asset
 * name, a description or any other tenant content.
 */
@Component
public class PredictiveInsightRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(PredictiveInsightRefreshScheduler.class);

    /** Same flag that gates every AI surface; a tenant opted out is not analysed. */
    static final String AI_FLAG = "commercial.governed-ai";

    private final OrganisationRepository       organisationRepository;
    private final FeatureFlagService           featureFlags;
    private final PredictiveMaintenanceService predictiveService;

    /** Assets analysed per tenant per run. */
    @Value("${app.insights.refresh.max-assets-per-organisation:2000}")
    private int maxAssetsPerOrganisation;

    /** Tenants processed per run, so the job's worst case stays bounded as we grow. */
    @Value("${app.insights.refresh.max-organisations:500}")
    private int maxOrganisations;

    public PredictiveInsightRefreshScheduler(OrganisationRepository organisationRepository,
                                             FeatureFlagService featureFlags,
                                             PredictiveMaintenanceService predictiveService) {
        this.organisationRepository = organisationRepository;
        this.featureFlags           = featureFlags;
        this.predictiveService      = predictiveService;
    }

    /**
     * Daily at 02:15 UTC by default — off-peak for the tenants this serves, and
     * offset from the top of the hour so it does not pile onto every other
     * hourly job. Override with {@code app.insights.refresh.cron}; set it to
     * {@code -} to disable the job entirely.
     */
    @Scheduled(cron = "${app.insights.refresh.cron:0 15 2 * * *}", zone = "UTC")
    @SchedulerLock(name = "predictiveInsightRefresh", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
    public void refreshAllTenants() {
        List<Organisation> organisations = organisationRepository.findAllByDeletedAtIsNull();

        int considered = 0;
        int refreshed  = 0;
        int skipped    = 0;
        int failed     = 0;
        int insights   = 0;

        for (Organisation org : organisations) {
            if (considered >= maxOrganisations) {
                log.warn("[InsightRefresh] Stopped after {} organisations ({} remain for the next run)",
                        considered, organisations.size() - considered);
                break;
            }
            considered++;

            if (!featureFlags.isEnabledFor(AI_FLAG, org.getId())) {
                skipped++;
                continue;
            }
            try {
                insights += refreshOne(org);
                refreshed++;
            } catch (DataIntegrityViolationException conflict) {
                // Someone pressed Regenerate for this tenant while we were mid-run.
                // Their set is computed from the same rows, so there is nothing to redo.
                failed++;
                log.info("[InsightRefresh] org={} skipped: a concurrent refresh won", org.getId());
            } catch (RuntimeException e) {
                failed++;
                // Class and message only: an exception from a tenant's data can
                // carry that data in its message.
                log.warn("[InsightRefresh] org={} failed: {}", org.getId(), e.getClass().getSimpleName());
            }
        }

        if (failed > 0) {
            log.warn("[InsightRefresh] Partial run: {} refreshed, {} skipped (feature off), {} failed, {} insights written",
                    refreshed, skipped, failed, insights);
        } else {
            log.info("[InsightRefresh] {} refreshed, {} skipped (feature off), {} insights written",
                    refreshed, skipped, insights);
        }
    }

    /**
     * One tenant, one transaction. {@code TenantContext} is set for the duration
     * because the service resolves its organisation from it, and cleared in a
     * finally so a failure cannot leak one tenant's id into the next iteration —
     * the scheduler thread is pooled and reused.
     */
    int refreshOne(Organisation org) {
        TenantContext.setOrganisationId(org.getId());
        try {
            return predictiveService.refreshInsights(maxAssetsPerOrganisation);
        } finally {
            TenantContext.clear();
        }
    }

    void configure(int maxAssetsPerOrganisation, int maxOrganisations) {
        this.maxAssetsPerOrganisation = maxAssetsPerOrganisation;
        this.maxOrganisations         = maxOrganisations;
    }
}
