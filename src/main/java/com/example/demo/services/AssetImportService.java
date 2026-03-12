package com.example.demo.services;

import com.example.demo.dto.AssetImportResultDto;
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
}
