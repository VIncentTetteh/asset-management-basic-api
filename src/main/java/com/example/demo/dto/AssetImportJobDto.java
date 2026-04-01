package com.example.demo.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AssetImportJobDto {
    private UUID jobId;
    private String status;
    private boolean dryRun;
    private AssetImportResultDto result; // null until completed/failed
}

