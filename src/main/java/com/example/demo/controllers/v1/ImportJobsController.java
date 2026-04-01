package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetImportJobDto;
import com.example.demo.services.AssetImportJobService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/import-jobs")
public class ImportJobsController {

    private final AssetImportJobService assetImportJobService;

    public ImportJobsController(AssetImportJobService assetImportJobService) {
        this.assetImportJobService = assetImportJobService;
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<AssetImportJobDto> createAssetImportJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        AssetImportJobDto dto = assetImportJobService.createAssetImportJob(file, dryRun, idempotencyKey);
        return ResponseEntity.accepted().body(dto);
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<AssetImportJobDto> getAssetImportJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(assetImportJobService.getAssetImportJob(jobId));
    }
}

