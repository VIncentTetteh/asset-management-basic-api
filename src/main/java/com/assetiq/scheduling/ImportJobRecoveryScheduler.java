package com.assetiq.scheduling;

import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.repositories.AssetImportJobRepository;
import com.assetiq.services.impl.AssetImportJobProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled recovery guard for bulk-import jobs.
 *
 * <p>The primary dispatch path ({@link com.assetiq.services.impl.AssetImportJobServiceImpl})
 * fires an {@code @Async} virtual-thread task after the transaction commits.  If the JVM
 * is killed (OOM, SIGKILL, rolling restart) between the DB commit and the task start, the
 * job stays in {@code QUEUED} indefinitely.  Similarly, a worker that crashes mid-run
 * leaves the row in {@code PROCESSING}.
 *
 * <p>This scheduler runs every 10 minutes (configurable) and re-fires any job that has
 * been stuck beyond the expected execution windows:
 * <ul>
 *   <li>{@code QUEUED} for more than {@code app.import.recovery.queued-timeout} (default 5 min)</li>
 *   <li>{@code PROCESSING} for more than {@code app.import.recovery.processing-timeout} (default 30 min)</li>
 * </ul>
 */
@Component
public class ImportJobRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRecoveryScheduler.class);

    private final AssetImportJobRepository jobRepository;
    private final AssetImportJobProcessor processor;

    /** How long a QUEUED job may sit before being considered stuck (default 5 min). */
    @Value("${app.import.recovery.queued-timeout-seconds:300}")
    private long queuedTimeoutSeconds;

    /** How long a PROCESSING job may run before being considered crashed (default 30 min). */
    @Value("${app.import.recovery.processing-timeout-seconds:1800}")
    private long processingTimeoutSeconds;

    public ImportJobRecoveryScheduler(AssetImportJobRepository jobRepository,
                                      AssetImportJobProcessor processor) {
        this.jobRepository = jobRepository;
        this.processor = processor;
    }

    /**
     * Run every 10 minutes.  Fixed delay (not rate) so overlapping runs are impossible
     * even if a previous recovery cycle is slow.
     */
    @Scheduled(fixedDelayString = "${app.import.recovery.interval-ms:600000}")
    void recoverStuckJobs() {
        Instant now = Instant.now();

        // Jobs stuck in QUEUED — pick the more conservative timeout
        Instant queuedCutoff = now.minus(Duration.ofSeconds(queuedTimeoutSeconds));
        // Jobs stuck in PROCESSING — longer window since actual Excel processing takes time
        Instant processingCutoff = now.minus(Duration.ofSeconds(processingTimeoutSeconds));

        // Single query for both statuses; we apply per-status cutoffs below
        List<AssetImportJob> candidates = jobRepository.findStuckJobs(
                List.of(ImportJobStatus.QUEUED, ImportJobStatus.PROCESSING),
                queuedCutoff  // oldest possible cutoff; PROCESSING cutoff applied below
        );

        // Filter by appropriate cutoff per status and re-fire
        int recovered = 0;
        for (AssetImportJob job : candidates) {
            boolean stuck = switch (job.getStatus()) {
                case QUEUED     -> job.getCreatedAt().isBefore(queuedCutoff);
                case PROCESSING -> job.getCreatedAt().isBefore(processingCutoff);
                default         -> false;
            };

            if (!stuck) continue;

            UUID jobId = job.getId();
            log.warn("[ImportRecovery] Re-firing stuck {} job {} (created {})",
                    job.getStatus(), jobId, job.getCreatedAt());
            try {
                processor.processAssetImportJob(jobId);
                recovered++;
            } catch (Exception ex) {
                log.error("[ImportRecovery] Failed to re-fire job {}: {}", jobId, ex.getMessage(), ex);
            }
        }

        if (recovered > 0) {
            log.info("[ImportRecovery] Recovered {} stuck import job(s)", recovered);
        }
    }
}
