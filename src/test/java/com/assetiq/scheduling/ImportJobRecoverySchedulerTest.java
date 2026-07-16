package com.assetiq.scheduling;

import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.repositories.AssetImportJobRepository;
import com.assetiq.services.impl.AssetImportJobProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportJobRecoveryScheduler")
class ImportJobRecoverySchedulerTest {

    @Mock AssetImportJobRepository jobRepository;
    @Mock AssetImportJobProcessor  processor;

    ImportJobRecoveryScheduler scheduler;

    // Use short, deterministic timeouts so we can control "stuck" state easily
    private static final long QUEUED_TIMEOUT_SECONDS     = 300;   //  5 min
    private static final long PROCESSING_TIMEOUT_SECONDS = 1800;  // 30 min

    @BeforeEach
    void setUp() {
        scheduler = new ImportJobRecoveryScheduler(jobRepository, processor);
        ReflectionTestUtils.setField(scheduler, "queuedTimeoutSeconds",     QUEUED_TIMEOUT_SECONDS);
        ReflectionTestUtils.setField(scheduler, "processingTimeoutSeconds", PROCESSING_TIMEOUT_SECONDS);
    }

    // ── No stuck jobs ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("does nothing when no stuck jobs are found")
    void noStuckJobs_noProcessorCalls() {
        when(jobRepository.findStuckJobs(any(), any())).thenReturn(Collections.emptyList());

        scheduler.recoverStuckJobs();

        verifyNoInteractions(processor);
    }

    // ── QUEUED timeout ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("QUEUED jobs")
    class QueuedJobs {

        @Test
        @DisplayName("re-fires a QUEUED job stuck beyond the queued timeout")
        void stuckQueuedJob_isRefired() {
            AssetImportJob job = queuedJob(Instant.now().minusSeconds(QUEUED_TIMEOUT_SECONDS + 10));
            when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(job));

            scheduler.recoverStuckJobs();

            verify(processor).processAssetImportJob(job.getId());
        }

        @Test
        @DisplayName("does NOT re-fire a QUEUED job that is within the queued timeout")
        void freshQueuedJob_isNotRefired() {
            // Created only 60 s ago — within the 300 s QUEUED window
            AssetImportJob job = queuedJob(Instant.now().minusSeconds(60));
            when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(job));

            scheduler.recoverStuckJobs();

            verifyNoInteractions(processor);
        }
    }

    // ── PROCESSING timeout ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PROCESSING jobs")
    class ProcessingJobs {

        @Test
        @DisplayName("re-fires a PROCESSING job stuck beyond the processing timeout")
        void stuckProcessingJob_isRefired() {
            AssetImportJob job = processingJob(Instant.now().minusSeconds(PROCESSING_TIMEOUT_SECONDS + 10));
            when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(job));

            scheduler.recoverStuckJobs();

            verify(processor).processAssetImportJob(job.getId());
        }

        @Test
        @DisplayName("does NOT re-fire a PROCESSING job within the processing timeout")
        void freshProcessingJob_isNotRefired() {
            // Created only 600 s ago — within the 1800 s PROCESSING window
            AssetImportJob job = processingJob(Instant.now().minusSeconds(600));
            when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(job));

            scheduler.recoverStuckJobs();

            verifyNoInteractions(processor);
        }

        @Test
        @DisplayName("a PROCESSING job stuck beyond QUEUED timeout but within PROCESSING timeout is not re-fired")
        void processingJobBeyondQueuedButWithinProcessingTimeout_isNotRefired() {
            // 400 s old — past QUEUED timeout (300 s) but within PROCESSING timeout (1800 s)
            AssetImportJob job = processingJob(Instant.now().minusSeconds(400));
            when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(job));

            scheduler.recoverStuckJobs();

            verifyNoInteractions(processor);
        }
    }

    // ── Mixed batch ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("recovers multiple stuck jobs in a single cycle")
    void multipleStuckJobs_allRefired() {
        AssetImportJob j1 = queuedJob(Instant.now().minusSeconds(QUEUED_TIMEOUT_SECONDS + 10));
        AssetImportJob j2 = queuedJob(Instant.now().minusSeconds(QUEUED_TIMEOUT_SECONDS + 20));
        AssetImportJob j3 = processingJob(Instant.now().minusSeconds(PROCESSING_TIMEOUT_SECONDS + 5));
        when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(j1, j2, j3));

        scheduler.recoverStuckJobs();

        verify(processor, times(3)).processAssetImportJob(any());
    }

    @Test
    @DisplayName("continues recovery even when processor throws for one job")
    void processorThrows_continuesWithRemaining() {
        AssetImportJob j1 = queuedJob(Instant.now().minusSeconds(QUEUED_TIMEOUT_SECONDS + 10));
        AssetImportJob j2 = queuedJob(Instant.now().minusSeconds(QUEUED_TIMEOUT_SECONDS + 10));
        when(jobRepository.findStuckJobs(any(), any())).thenReturn(List.of(j1, j2));
        doThrow(new RuntimeException("processor error")).when(processor).processAssetImportJob(j1.getId());

        scheduler.recoverStuckJobs(); // must not throw

        // j2 should still be attempted despite j1's failure
        verify(processor).processAssetImportJob(j2.getId());
    }

    // ── Repository query cutoff ───────────────────────────────────────────────

    @Test
    @DisplayName("queries repository with the QUEUED cutoff instant (most conservative)")
    void queriesRepositoryWithQueuedCutoff() {
        when(jobRepository.findStuckJobs(any(), any())).thenReturn(Collections.emptyList());

        Instant before = Instant.now();
        scheduler.recoverStuckJobs();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).findStuckJobs(
                eq(List.of(ImportJobStatus.QUEUED, ImportJobStatus.PROCESSING)),
                cutoffCaptor.capture());

        Instant captured = cutoffCaptor.getValue();
        // Captured cutoff should be approximately (now - queuedTimeout)
        assertThat(captured).isBefore(before.minusSeconds(QUEUED_TIMEOUT_SECONDS - 2));
        assertThat(captured).isAfter(after.minusSeconds(QUEUED_TIMEOUT_SECONDS + 2));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AssetImportJob queuedJob(Instant createdAt) {
        AssetImportJob job = new AssetImportJob();
        job.setId(UUID.randomUUID());
        job.setStatus(ImportJobStatus.QUEUED);
        job.setCreatedAt(createdAt);
        return job;
    }

    private AssetImportJob processingJob(Instant createdAt) {
        AssetImportJob job = new AssetImportJob();
        job.setId(UUID.randomUUID());
        job.setStatus(ImportJobStatus.PROCESSING);
        job.setCreatedAt(createdAt);
        return job;
    }
}
