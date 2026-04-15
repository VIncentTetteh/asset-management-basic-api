package com.assetiq.services.impl;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetImportJobRepository;
import com.assetiq.services.AssetImportService;
import com.assetiq.storage.FileStorageService;
import com.assetiq.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AssetImportJobProcessor {

    private final AssetImportJobRepository jobRepository;
    private final AssetImportService assetImportService;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;

    public AssetImportJobProcessor(
            AssetImportJobRepository jobRepository,
            AssetImportService assetImportService,
            FileStorageService storageService,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.assetImportService = assetImportService;
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

            AssetImportResultDto result = assetImportService.importFromExcelBytes(
                    job.getFilename(),
                    job.getContentType(),
                    stored.bytes(),
                    job.isDryRun()
            );

            job.setTotalRows(result.getTotalRows());
            job.setImported(result.getImported());
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
}

