package com.assetiq.services.impl;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.enums.ImportJobStatus;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportExecutionService;
import com.assetiq.imports.ImportOptions;
import com.assetiq.models.AssetImportJob;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetImportJobRepository;
import com.assetiq.services.AssetImportService;
import com.assetiq.storage.FileStorageService;
import com.assetiq.storage.StoredObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * The async worker behind every import job, whatever it imports.
 *
 * <p>Two paths in, one machine: a job with no stored mapping is the historical
 * positional asset import and goes through {@link AssetImportService}; a job with a
 * mapping is a wizard commit and goes through {@link ImportExecutionService} with its
 * entity type's handler. Both write their outcome back to the same row, so status
 * polling, the stuck-job recovery scheduler and the result shape are shared.</p>
 */
@Service
public class AssetImportJobProcessor {

    private final AssetImportJobRepository jobRepository;
    private final AssetImportService assetImportService;
    private final ImportExecutionService importExecutionService;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;

    public AssetImportJobProcessor(
            AssetImportJobRepository jobRepository,
            AssetImportService assetImportService,
            ImportExecutionService importExecutionService,
            FileStorageService storageService,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.assetImportService = assetImportService;
        this.importExecutionService = importExecutionService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void processAssetImportJob(UUID jobId) {
        AssetImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        TenantContext.setOrganisationId(job.getOrganisation().getId());
        job.setStatus(ImportJobStatus.PROCESSING);
        jobRepository.save(job);

        try {
            StoredObject stored = storageService.get(job.getStorageKey())
                    .orElseThrow(() -> new IllegalStateException("Import file not found for job"));

            AssetImportResultDto result = run(job, stored.bytes());

            job.setTotalRows(result.getTotalRows());
            job.setImported(result.getImported());
            job.setUpdatedRows(result.getUpdated());
            job.setSkipped(result.getSkipped());
            job.setErrorsJson(objectMapper.writeValueAsString(result.getErrors()));
            job.setStatus(ImportJobStatus.COMPLETED);
            job.setErrorSummary(null);
            jobRepository.save(job);
        } catch (Exception e) {
            job.setStatus(ImportJobStatus.FAILED);
            job.setErrorSummary(e.getMessage());
            jobRepository.save(job);
        } finally {
            TenantContext.clear();
        }
    }

    private AssetImportResultDto run(AssetImportJob job, byte[] bytes) throws Exception {
        if (job.getMappingJson() == null || job.getMappingJson().isBlank()) {
            // No mapping: the historical positional asset layout, unchanged.
            return assetImportService.importFromExcelBytes(
                    job.getFilename(), job.getContentType(), bytes, job.isDryRun());
        }

        ImportEntityType type = ImportEntityType.fromSlug(job.getEntityType())
                .orElse(ImportEntityType.ASSETS);
        Map<String, Integer> mapping = objectMapper.readValue(
                job.getMappingJson(), new TypeReference<Map<String, Integer>>() {});
        ImportOptions options = job.getOptionsJson() == null || job.getOptionsJson().isBlank()
                ? ImportOptions.defaults()
                : objectMapper.readValue(job.getOptionsJson(), ImportOptions.class);

        return importExecutionService.execute(
                type, job.getOrganisation(), job.getFilename(), bytes, mapping,
                options.withDryRun(job.isDryRun()), 0);
    }
}
