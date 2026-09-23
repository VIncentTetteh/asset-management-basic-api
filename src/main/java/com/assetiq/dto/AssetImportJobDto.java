package com.assetiq.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AssetImportJobDto {
    private UUID jobId;
    private String status;
    /** Which record type this job imports, as the API slug (assets, suppliers, ...). */
    private String entityType;
    private boolean dryRun;
    private AssetImportResultDto result; // null until completed/failed
}

