package com.assetiq.services.impl;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.AssetImportResultDto.RowError;
import com.assetiq.imports.ImportEngine;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ParsedSheet;
import com.assetiq.imports.SpreadsheetReader;
import com.assetiq.imports.handlers.AssetImportHandler;
import com.assetiq.models.Organisation;
import com.assetiq.security.SpreadsheetUploadPolicy;
import com.assetiq.services.AssetImportService;
import com.assetiq.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The original, positional asset import: an .xlsx whose first sheet carries the columns
 * in one exact order —
 *
 * <pre>
 *  name | assetTag | serialNumber | description | assetType | manufacturer | model |
 *  purchaseDate | purchaseCost | currency | depreciationMethod | usefulLifeMonths |
 *  residualValue | warrantyExpiryDate | status | condition |
 *  category | location | supplier | department | assignedUserEmail |
 *  invoiceId | insurancePolicyId | ...extra columns
 * </pre>
 *
 * <p>It no longer parses rows itself. It builds the positional mapping — column
 * <i>i</i> to the <i>i</i>th descriptor of {@link AssetImportHandler} — and hands the
 * file to the generic {@link ImportEngine}. The engine and the handler are the same
 * code the mapping-driven wizard runs, so the two paths cannot drift: a fix to date
 * parsing or reference resolution lands in both, and this entry point stays supported
 * for existing callers and integrations that post the historical layout.</p>
 *
 * <p>Duplicates fail the row here rather than being skipped, which is what this path
 * has always done; the wizard exposes the choice as an option.</p>
 */
@Service
public class AssetImportServiceImpl extends com.assetiq.services.TenantAwareService
        implements AssetImportService {

    /** @deprecated use {@link AssetImportHandler#CUSTOM_FIELDS_FLAG}; kept for callers and tests. */
    @Deprecated
    public static final String CUSTOM_FIELDS_FLAG = AssetImportHandler.CUSTOM_FIELDS_FLAG;

    private final AssetImportHandler assetImportHandler;
    private final ImportEngine importEngine;
    private final SpreadsheetReader spreadsheetReader;
    private final FileStorageService storageService;

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    public AssetImportServiceImpl(
            com.assetiq.repositories.OrganisationRepository organisationRepository,
            AssetImportHandler assetImportHandler,
            ImportEngine importEngine,
            SpreadsheetReader spreadsheetReader,
            FileStorageService storageService) {
        super(organisationRepository);
        this.assetImportHandler = assetImportHandler;
        this.importEngine = importEngine;
        this.spreadsheetReader = spreadsheetReader;
        this.storageService = storageService;
    }

    @Override
    public AssetImportResultDto importFromExcel(MultipartFile file) {
        return importFromExcel(file, false);
    }

    @Override
    public AssetImportResultDto importFromExcel(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            return failed(dryRun, "Uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            return failed(dryRun, "Only macro-free .xlsx files are supported");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            return failed(dryRun, "Failed to read uploaded file");
        }

        return importFromExcelInternal(filename, file.getContentType(), fileBytes, dryRun, true);
    }

    @Override
    public AssetImportResultDto importFromExcelBytes(String filename, String contentType, byte[] fileBytes, boolean dryRun) {
        // For async import jobs, bytes are already provided; we avoid re-storing import artifacts here.
        return importFromExcelInternal(filename, contentType, fileBytes, dryRun, false);
    }

    private AssetImportResultDto importFromExcelInternal(String filename,
                                                         String contentType,
                                                         byte[] fileBytes,
                                                         boolean dryRun,
                                                         boolean storeArtifact) {
        if (fileBytes == null || fileBytes.length == 0) {
            return failed(dryRun, "Uploaded file is empty");
        }
        try {
            SpreadsheetUploadPolicy.validate(filename, fileBytes);
        } catch (IllegalArgumentException rejected) {
            return failed(dryRun, rejected.getMessage());
        }

        Organisation org;
        try {
            org = requireTenantOrg();
        } catch (AccessDeniedException e) {
            return failed(dryRun, e.getMessage());
        }

        if (storeArtifact) {
            String cleanName = SpreadsheetUploadPolicy.sanitiseFilename(filename);
            String key = importPrefix + "/" + org.getId() + "/" + UUID.randomUUID() + "/" + cleanName;
            storageService.store(key, fileBytes, SpreadsheetUploadPolicy.XLSX_CONTENT_TYPE, cleanName, Map.of(
                    "organisationId", org.getId().toString(),
                    "originalFilename", cleanName
            ));
        }

        ParsedSheet sheet;
        try {
            sheet = spreadsheetReader.read(filename, fileBytes);
        } catch (IllegalArgumentException unreadable) {
            return failed(dryRun, unreadable.getMessage());
        }

        // FAIL on duplicates and read the extra columns: both are what this path has
        // always done. captureUnmappedColumns is true here and nowhere else — columns
        // past the fixed 23 become custom fields behind the tenant's feature flag, and
        // something may depend on that. The mapping-driven wizard ignores unmapped
        // columns instead.
        ImportOptions options = new ImportOptions(
                ImportOptions.DuplicateStrategy.FAIL, false, dryRun, true, true);
        return importEngine.run(sheet, positionalMapping(sheet), assetImportHandler, options, org, 0);
    }

    /**
     * Column <i>i</i> feeds the <i>i</i>th declared field. Columns past the declared set
     * stay unmapped and become custom fields, exactly as the positional parser did.
     */
    private Map<String, Integer> positionalMapping(ParsedSheet sheet) {
        List<ImportFieldDescriptor> fields = assetImportHandler.fields();
        Map<String, Integer> mapping = new LinkedHashMap<>();
        for (int i = 0; i < fields.size() && i < sheet.columnCount(); i++) {
            mapping.put(fields.get(i).name(), i);
        }
        return mapping;
    }

    private AssetImportResultDto failed(boolean dryRun, String message) {
        AssetImportResultDto result = new AssetImportResultDto();
        result.setDryRun(dryRun);
        result.getErrors().add(new RowError(0, message));
        return result;
    }
}
