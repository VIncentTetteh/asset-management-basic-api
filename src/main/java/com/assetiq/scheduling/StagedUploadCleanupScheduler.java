package com.assetiq.scheduling;

import com.assetiq.models.ImportStagedUpload;
import com.assetiq.repositories.ImportStagedUploadRepository;
import com.assetiq.storage.FileStorageService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deletes import files that were uploaded to the wizard and never committed.
 *
 * <p>Each staged upload carries an {@code expiresAt}; this removes the stored object
 * and soft-deletes the row once that passes. Without it the staging area only grows —
 * every abandoned wizard session, every customer who uploaded the wrong file, kept
 * forever at up to 10 MB a time. That is a slow-motion outage and a data-retention
 * problem at once, since the files are customer records.</p>
 *
 * <p>Committing an upload copies its bytes to the job's own key, so cleaning up the
 * staged copy never pulls the file out from under a running or completed job.</p>
 */
@Component
public class StagedUploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(StagedUploadCleanupScheduler.class);

    private final ImportStagedUploadRepository stagedUploadRepository;
    private final FileStorageService storageService;

    public StagedUploadCleanupScheduler(ImportStagedUploadRepository stagedUploadRepository,
                                        FileStorageService storageService) {
        this.stagedUploadRepository = stagedUploadRepository;
        this.storageService = storageService;
    }

    /**
     * Hourly, in batches. Fixed delay so a slow storage backend cannot overlap runs,
     * and ShedLock so only one instance does the deleting.
     */
    @Scheduled(fixedDelayString = "${app.import.staging.cleanup-interval-ms:3600000}")
    @SchedulerLock(name = "importStagedUploadCleanup", lockAtMostFor = "PT20M", lockAtLeastFor = "PT30S")
    @Transactional
    public void purgeExpiredUploads() {
        List<ImportStagedUpload> expired =
                stagedUploadRepository.findTop200ByExpiresAtBeforeAndDeletedAtIsNull(Instant.now());
        if (expired.isEmpty()) {
            return;
        }

        int purged = 0;
        for (ImportStagedUpload upload : expired) {
            try {
                storageService.delete(upload.getStorageKey());
            } catch (Exception e) {
                // The row is still marked deleted: a storage object we failed to remove
                // is a leak to chase in the backend's own lifecycle rules, not a reason
                // to keep serving an expired upload.
                log.warn("[ImportStagingCleanup] Could not delete stored object {}: {}",
                        upload.getStorageKey(), e.getMessage());
            }
            upload.setDeletedAt(Instant.now());
            stagedUploadRepository.save(upload);
            purged++;
        }
        log.info("[ImportStagingCleanup] Purged {} expired staged upload(s)", purged);
    }
}
