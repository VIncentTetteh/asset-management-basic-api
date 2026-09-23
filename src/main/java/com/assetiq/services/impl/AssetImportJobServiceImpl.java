package com.assetiq.services.impl;

import com.assetiq.dto.AssetImportJobDto;
import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.enums.ImportJobStatus;
import com.assetiq.models.AssetImportJob;
import com.assetiq.models.IdempotencyRecord;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetImportJobRepository;
import com.assetiq.repositories.IdempotencyRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportPermissions;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportWizardService;
import com.assetiq.models.ImportStagedUpload;
import com.assetiq.services.AssetImportJobService;
import com.assetiq.services.AssetImportService;
import com.assetiq.storage.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.assetiq.security.SpreadsheetUploadPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AssetImportJobServiceImpl extends com.assetiq.services.TenantAwareService implements AssetImportJobService {

    private final AssetImportJobRepository jobRepository;
    private final AssetImportService assetImportService;
    private final AssetImportJobProcessor assetImportJobProcessor;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ImportWizardService importWizardService;
    private final ImportPermissions importPermissions;

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    public AssetImportJobServiceImpl(
            OrganisationRepository organisationRepository,
            AssetImportJobRepository jobRepository,
            AssetImportService assetImportService,
            AssetImportJobProcessor assetImportJobProcessor,
            IdempotencyRecordRepository idempotencyRecordRepository,
            FileStorageService storageService,
            ImportWizardService importWizardService,
            ImportPermissions importPermissions,
            ObjectMapper objectMapper) {
        super(organisationRepository);
        this.importWizardService = importWizardService;
        this.importPermissions = importPermissions;
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
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }
        SpreadsheetUploadPolicy.validate(filename, fileBytes);

        String cleanName = SpreadsheetUploadPolicy.sanitiseFilename(filename);
        String contentType = SpreadsheetUploadPolicy.XLSX_CONTENT_TYPE;

        String operation = "import-jobs/assets";
        String trimmedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        String requestHash = null;

        if (trimmedIdempotencyKey != null && !trimmedIdempotencyKey.isBlank()) {
            requestHash = computeRequestHash(fileBytes, dryRun, cleanName, contentType);
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

        storageService.store(key, fileBytes, contentType, cleanName, Map.of(
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
        job.setContentType(contentType);
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
        dto.setEntityType(ImportEntityType.ASSETS.slug());
        dto.setDryRun(dryRun);
        dto.setStatus(job.getStatus().name());
        dto.setResult(null);
        return dto;
    }

    /**
     * Commit a staged upload.
     *
     * <p>The file's bytes are copied into the job's own storage key rather than
     * referenced where they lie: the staged copy is on a clock and the cleanup job will
     * delete it, and a job whose input vanished mid-run is not a failure mode worth
     * having. The staged row itself is left alone so the same upload can be committed
     * again (a first pass as a dry run, then for real).</p>
     */
    @Override
    @Transactional
    public AssetImportJobDto createMappedImportJob(ImportEntityType type,
                                                   UUID uploadId,
                                                   Map<String, Integer> mapping,
                                                   ImportOptions options,
                                                   String idempotencyKey) {
        importPermissions.require(type);
        Organisation org = requireTenantOrg();
        ImportStagedUpload staged = importWizardService.requireStagedUpload(type, org, uploadId);
        byte[] fileBytes = importWizardService.stagedBytes(staged);
        ImportOptions effectiveOptions = options == null ? ImportOptions.defaults() : options;

        String operation = "import-jobs/" + type.slug();
        String trimmedIdempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        String requestHash = null;
        if (trimmedIdempotencyKey != null && !trimmedIdempotencyKey.isBlank()) {
            requestHash = computeRequestHash(fileBytes, effectiveOptions.dryRun(),
                    staged.getFilename() + "|" + toJson(mapping), staged.getContentType());
            var existing = idempotencyRecordRepository
                    .findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                            org, operation, trimmedIdempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getRequestHash().equals(requestHash)) {
                    throw new IllegalStateException("Idempotency key already used with a different request payload");
                }
                return getAssetImportJob(existing.get().getResponseJobId());
            }
        }

        UUID jobId = UUID.randomUUID();
        String key = importPrefix + "/jobs/" + org.getId() + "/" + jobId + "/" + staged.getFilename();
        storageService.store(key, fileBytes, staged.getContentType(), staged.getFilename(), Map.of(
                "organisationId", org.getId().toString(),
                "jobId", jobId.toString(),
                "originalFilename", staged.getFilename()
        ));

        AssetImportJob job = new AssetImportJob();
        job.setId(jobId);
        job.setOrganisation(org);
        job.setEntityType(type.name());
        job.setDryRun(effectiveOptions.dryRun());
        job.setStatus(ImportJobStatus.QUEUED);
        job.setStorageKey(key);
        job.setFilename(staged.getFilename());
        job.setContentType(staged.getContentType());
        job.setMappingJson(toJson(mapping == null ? Map.of() : mapping));
        job.setOptionsJson(toJson(effectiveOptions));
        jobRepository.save(job);

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
                return idempotencyRecordRepository
                        .findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
                                org, operation, trimmedIdempotencyKey)
                        .map(existing -> getAssetImportJob(existing.getResponseJobId()))
                        .orElseThrow(() -> e);
            }
        }

        fireAfterCommit(jobId);

        AssetImportJobDto dto = new AssetImportJobDto();
        dto.setJobId(jobId);
        dto.setEntityType(type.slug());
        dto.setDryRun(effectiveOptions.dryRun());
        dto.setStatus(job.getStatus().name());
        dto.setResult(null);
        return dto;
    }

    private void fireAfterCommit(UUID jobId) {
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
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise import job metadata", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AssetImportJobDto getAssetImportJob(UUID jobId) {
        Organisation org = requireTenantOrg();
        AssetImportJob job = jobRepository.findByIdAndOrganisationAndDeletedAtIsNull(jobId, org)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found"));

        // The job row knows what it imported, so the status read is authorised against
        // that type rather than against whatever the polling endpoint happens to allow.
        ImportEntityType jobType = ImportEntityType.fromSlug(job.getEntityType())
                .orElse(ImportEntityType.ASSETS);
        importPermissions.require(jobType);

        AssetImportJobDto dto = new AssetImportJobDto();
        dto.setJobId(job.getId());
        dto.setEntityType(ImportEntityType.fromSlug(job.getEntityType())
                .orElse(ImportEntityType.ASSETS).slug());
        dto.setDryRun(job.isDryRun());
        dto.setStatus(job.getStatus().name());

        if (job.getStatus() == ImportJobStatus.COMPLETED || job.getStatus() == ImportJobStatus.FAILED) {
            AssetImportResultDto result = new AssetImportResultDto();
            result.setDryRun(job.isDryRun());
            result.setTotalRows(job.getTotalRows());
            result.setImported(job.getImported());
            result.setUpdated(job.getUpdatedRows());
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
