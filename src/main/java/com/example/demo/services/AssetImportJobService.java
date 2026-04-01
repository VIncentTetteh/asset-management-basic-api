package com.example.demo.services;

import com.example.demo.dto.AssetImportJobDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AssetImportJobService {
    AssetImportJobDto createAssetImportJob(MultipartFile file, boolean dryRun, String idempotencyKey);

    AssetImportJobDto getAssetImportJob(UUID jobId);
}

