package com.assetiq.services;

import com.assetiq.dto.AssetImportResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface AssetImportService {

    /**
     * Parse an Excel (.xlsx) file and bulk-import assets for the current tenant.
     * Each data row is validated individually; rows with errors are skipped and reported.
     *
     * @param file the uploaded .xlsx file
     * @return a summary of the import (total rows, imported, skipped, per-row errors)
     */
    AssetImportResultDto importFromExcel(MultipartFile file);

    /**
     * Import and optionally run in dry-run mode (validate without persisting assets).
     */
    AssetImportResultDto importFromExcel(MultipartFile file, boolean dryRun);

    /**
     * Parse and optionally validate only, using already-loaded bytes.
     * This is useful for async import jobs where the file must survive across threads.
     */
    AssetImportResultDto importFromExcelBytes(String filename, String contentType, byte[] fileBytes, boolean dryRun);
}
