package com.example.demo.services.impl;

import com.example.demo.dto.AssetImportJobDto;
import com.example.demo.dto.AssetImportResultDto;
import com.example.demo.enums.ImportJobStatus;
import com.example.demo.models.AssetImportJob;
import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.AssetImportJobRepository;
import com.example.demo.repositories.IdempotencyRecordRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.AssetImportJobService;
import com.example.demo.services.AssetImportService;
import com.example.demo.storage.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AssetImportJobServiceImpl extends com.example.demo.services.TenantAwareService implements AssetImportJobService {

    private final AssetImportJobRepository jobRepository;
    private final AssetImportService assetImportService;
    private final AssetImportJobProcessor assetImportJobProcessor;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    public AssetImportJobServiceImpl(
            OrganisationRepository organisationRepository,
            AssetImportJobRepository jobRepository,
            AssetImportService assetImportService,
            AssetImportJobProcessor assetImportJobProcessor,
            IdempotencyRecordRepository idempotencyRecordRepository,
            FileStorageService storageService,
            ObjectMapper objectMapper) {
        super(organisationRepository);
        this.jobRepository = jobRepository;
        this.assetImportService = assetImportService;
        this.assetImportJobProcessor = assetImportJobProcessor;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AssetImportJobDto createAssetImportJob(MultipartFile file, boolean dryRun, String idempotencyKey) {
        Organisation org = requireTenantOrg();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        String cleanName = filename.replaceAll("[^A-Za-z0-9._-]", "_");

        String operation = "import-jobs/assets";
        String trimmedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        String requestHash = null;

        if (trimmedIdempotencyKey != null && !trimmedIdempotencyKey.isBlank()) {
            requestHash = computeRequestHash(fileBytes, dryRun, cleanName, file.getContentType());
            var existing = idempotencyRecordRepository.findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                    org, operation, trimmedIdempotencyKey
            );
            if (existing.isPresent()) {
                if (!existing.get().getRequestHash().equals(requestHash)) {
                    throw new IllegalStateException("Idempotency key already used with a different request payload");
                }
                return getAssetImportJob(existing.get().getResponseJobId());
            }
        }

        UUID jobId = UUID.randomUUID();
        String key = importPrefix + "/jobs/" + org.getId() + "/" + jobId + "/" + cleanName;

        storageService.store(key, fileBytes, file.getContentType(), cleanName, Map.of(
                "organisationId", org.getId().toString(),
                "jobId", jobId.toString(),
                "originalFilename", cleanName
        ));

        AssetImportJob job = new AssetImportJob();
        job.setId(jobId);
        job.setOrganisation(org);
        job.setDryRun(dryRun);
        job.setStatus(ImportJobStatus.QUEUED);
        job.setStorageKey(key);
        job.setFilename(cleanName);
        job.setContentType(file.getContentType());
        jobRepository.save(job);

        // Persist idempotency mapping (key -> job) so retries return the same job.
        if (trimmedIdempotencyKey != null && requestHash != null) {
            try {
                IdempotencyRecord rec = new IdempotencyRecord();
                rec.setOrganisation(org);
                rec.setOperation(operation);
                rec.setIdempotencyKey(trimmedIdempotencyKey);
                rec.setRequestHash(requestHash);
                rec.setResponseJobId(jobId);
                idempotencyRecordRepository.save(rec);
            } catch (DataIntegrityViolationException e) {
                // Another concurrent request may have created the same idempotency key.
                // If it did, return the job referenced by the existing mapping.
                return idempotencyRecordRepository.findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                                org, operation, trimmedIdempotencyKey
                        )
                        .map(existing -> getAssetImportJob(existing.getResponseJobId()))
                        .orElseThrow(() -> e);
            }
        }

        // Fire-and-forget async processing, but only after the transaction commits,
        // otherwise the async thread may not be able to see the just-saved job row.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    assetImportJobProcessor.processAssetImportJob(jobId);
                }
            });
        } else {
            assetImportJobProcessor.processAssetImportJob(jobId);
        }

        AssetImportJobDto dto = new AssetImportJobDto();
        dto.setJobId(jobId);
        dto.setDryRun(dryRun);
        dto.setStatus(job.getStatus().name());
        dto.setResult(null);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public AssetImportJobDto getAssetImportJob(UUID jobId) {
        Organisation org = requireTenantOrg();
        AssetImportJob job = jobRepository.findByIdAndOrganisationAndDeletedAtIsNull(jobId, org)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found"));

        AssetImportJobDto dto = new AssetImportJobDto();
        dto.setJobId(job.getId());
        dto.setDryRun(job.isDryRun());
        dto.setStatus(job.getStatus().name());

        if (job.getStatus() == ImportJobStatus.COMPLETED || job.getStatus() == ImportJobStatus.FAILED) {
            AssetImportResultDto result = new AssetImportResultDto();
            result.setDryRun(job.isDryRun());
            result.setTotalRows(job.getTotalRows());
            result.setImported(job.getImported());
            result.setSkipped(job.getSkipped());
            result.getErrors().clear();

            if (job.getErrorsJson() != null && !job.getErrorsJson().isBlank()) {
                try {
                    List<AssetImportResultDto.RowError> errors = objectMapper.readValue(
                            job.getErrorsJson(),
                            new TypeReference<List<AssetImportResultDto.RowError>>() {}
                    );
                    result.getErrors().addAll(errors);
                } catch (Exception e) {
                    result.getErrors().add(new AssetImportResultDto.RowError(0, "Failed to parse job errors"));
                }
            }
            dto.setResult(result);
        } else {
            dto.setResult(null);
        }

        return dto;
    }

    private String computeRequestHash(byte[] fileBytes, boolean dryRun, String cleanName, String contentType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((Boolean.toString(dryRun) + "|").getBytes(StandardCharsets.UTF_8));
            digest.update((cleanName == null ? "" : cleanName).getBytes(StandardCharsets.UTF_8));
            digest.update("|".getBytes(StandardCharsets.UTF_8));
            digest.update((contentType == null ? "" : contentType).getBytes(StandardCharsets.UTF_8));
            digest.update("|".getBytes(StandardCharsets.UTF_8));
            digest.update(fileBytes);
            return toHexLower(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // Worker moved to AssetImportJobProcessor (so @Async works via Spring proxy)
}

