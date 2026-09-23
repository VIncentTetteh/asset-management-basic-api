package com.assetiq.services;

import com.assetiq.dto.AssetImportJobDto;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportOptions;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface AssetImportJobService {

    /** The historical asset path: a positional .xlsx posted straight to the job queue. */
    AssetImportJobDto createAssetImportJob(MultipartFile file, boolean dryRun, String idempotencyKey);

    /**
     * The wizard path: commit a previously staged upload with a hand-checked mapping.
     * Produces the same kind of job, polled through the same endpoint — there is one
     * import job system, not one per entity type.
     */
    AssetImportJobDto createMappedImportJob(ImportEntityType type,
                                            UUID uploadId,
                                            Map<String, Integer> mapping,
                                            ImportOptions options,
                                            String idempotencyKey);

    AssetImportJobDto getAssetImportJob(UUID jobId);
}
