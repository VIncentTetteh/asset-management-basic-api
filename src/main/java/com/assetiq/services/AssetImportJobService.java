package com.assetiq.services;

import com.assetiq.dto.AssetImportJobDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AssetImportJobService {
    AssetImportJobDto createAssetImportJob(MultipartFile file, boolean dryRun, String idempotencyKey);

    AssetImportJobDto getAssetImportJob(UUID jobId);
}

