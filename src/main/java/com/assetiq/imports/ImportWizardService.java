package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.ImportAnalysisDto;
import com.assetiq.dto.ImportDetectedColumnDto;
import com.assetiq.dto.ImportMappingPresetDto;
import com.assetiq.dto.ImportOptionsDto;
import com.assetiq.dto.ImportPreviewDto;
import com.assetiq.dto.ImportRunRequestDto;
import com.assetiq.dto.ImportTypeDto;
import com.assetiq.models.ImportMappingPreset;
import com.assetiq.models.ImportStagedUpload;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ImportMappingPresetRepository;
import com.assetiq.repositories.ImportStagedUploadRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.security.SpreadsheetUploadPolicy;
import com.assetiq.services.TenantAwareService;
import com.assetiq.storage.FileStorageService;
import com.assetiq.storage.StoredObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The wizard's server side: describe the types, hand out templates, stage an upload,
 * suggest a mapping, preview it, and save mappings for next time.
 *
 * <p>Everything here is tenant-scoped through {@link #requireTenantOrg()}; staged
 * uploads and presets are read only through repository methods that take the
 * organisation, so holding another tenant's id gets a 404, not their data.</p>
 */
@Service
public class ImportWizardService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(ImportWizardService.class);

    /** Rows a preview checks. Beyond this the answer stops being useful and starts
     *  being a second full import run on the request thread. */
    public static final int PREVIEW_ROW_LIMIT = 200;

    /** Sample values shown per detected column in the wizard. */
    private static final int SAMPLE_VALUES = 3;

    private final ImportDescriptorRegistry registry;
    private final ImportTemplateGenerator templateGenerator;
    private final SpreadsheetReader spreadsheetReader;
    private final ColumnMatcher columnMatcher;
    private final ImportExecutionService executionService;
    private final ImportStagedUploadRepository stagedUploadRepository;
    private final ImportMappingPresetRepository presetRepository;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    /** How long a staged upload lives before the cleanup job removes it. */
    @Value("${app.import.staging.ttl-hours:24}")
    private long stagingTtlHours;

    public ImportWizardService(OrganisationRepository organisationRepository,
                               ImportDescriptorRegistry registry,
                               ImportTemplateGenerator templateGenerator,
                               SpreadsheetReader spreadsheetReader,
                               ColumnMatcher columnMatcher,
                               ImportExecutionService executionService,
                               ImportStagedUploadRepository stagedUploadRepository,
                               ImportMappingPresetRepository presetRepository,
                               FileStorageService storageService,
                               ObjectMapper objectMapper) {
        super(organisationRepository);
        this.registry = registry;
        this.templateGenerator = templateGenerator;
        this.spreadsheetReader = spreadsheetReader;
        this.columnMatcher = columnMatcher;
        this.executionService = executionService;
        this.stagedUploadRepository = stagedUploadRepository;
        this.presetRepository = presetRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    public List<ImportTypeDto> listTypes() {
        return registry.supportedTypes().stream()
                .map(type -> new ImportTypeDto(type.slug(), type.label(), type.description()))
                .toList();
    }

    public List<ImportFieldDescriptor> fields(ImportEntityType type) {
        return registry.fields(type);
    }

    // ── Template ──────────────────────────────────────────────────────────────

    public record Template(byte[] bytes, String contentType, String filename) {}

    public Template template(ImportEntityType type, String format) {
        List<ImportFieldDescriptor> fields = registry.fields(type);
        String normalised = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(normalised) && !"csv".equals(normalised)) {
            throw new IllegalArgumentException("format must be xlsx or csv");
        }
        byte[] bytes = "csv".equals(normalised)
                ? templateGenerator.csv(fields)
                : templateGenerator.xlsx(type, fields);
        String contentType = "csv".equals(normalised)
                ? ImportUploadPolicy.CSV_CONTENT_TYPE
                : ImportUploadPolicy.XLSX_CONTENT_TYPE;
        return new Template(bytes, contentType, templateGenerator.filename(type, normalised));
    }

    // ── Analyse ───────────────────────────────────────────────────────────────

    @Transactional
    public ImportAnalysisDto analyse(ImportEntityType type, MultipartFile file) {
        Organisation org = requireTenantOrg();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        String cleanName = SpreadsheetUploadPolicy.sanitiseFilename(file.getOriginalFilename());
        String contentType = ImportUploadPolicy.validate(cleanName, bytes);

        ParsedSheet sheet = spreadsheetReader.read(cleanName, bytes);

        UUID uploadId = UUID.randomUUID();
        String storageKey = importPrefix + "/staged/" + org.getId() + "/" + uploadId + "/" + cleanName;
        storageService.store(storageKey, bytes, contentType, cleanName, Map.of(
                "organisationId", org.getId().toString(),
                "uploadId", uploadId.toString(),
                "originalFilename", cleanName
        ));

        List<ImportDetectedColumnDto> detected = new ArrayList<>();
        for (int i = 0; i < sheet.columnCount(); i++) {
            detected.add(new ImportDetectedColumnDto(i, sheet.headers().get(i),
                    sheet.sampleValues(i, SAMPLE_VALUES)));
        }

        List<ImportFieldDescriptor> fields = registry.fields(type);
        Map<String, Integer> suggested = columnMatcher.suggest(fields, sheet.headers());
        // Every field appears in the response, unmapped ones as null, so the wizard can
        // render the full form from this one payload.
        Map<String, Integer> fullMapping = new LinkedHashMap<>();
        fields.forEach(f -> fullMapping.put(f.name(), suggested.get(f.name())));

        Instant expiresAt = Instant.now().plus(Duration.ofHours(stagingTtlHours));
        ImportStagedUpload staged = new ImportStagedUpload();
        staged.setId(uploadId);
        staged.setOrganisation(org);
        staged.setEntityType(type.name());
        staged.setStorageKey(storageKey);
        staged.setFilename(cleanName);
        staged.setContentType(contentType);
        staged.setRowCount(sheet.rowCount());
        staged.setColumnCount(sheet.columnCount());
        staged.setColumnsJson(writeJson(detected));
        staged.setExpiresAt(expiresAt);
        stagedUploadRepository.save(staged);

        log.info("Import analyse: org={} type={} upload={} rows={} columns={} suggested={}",
                org.getId(), type.name(), uploadId, sheet.rowCount(), sheet.columnCount(), suggested.size());

        return new ImportAnalysisDto(
                uploadId,
                detected,
                fullMapping,
                sheet.rowCount(),
                columnMatcher.unmappedColumns(sheet.headers(), suggested),
                columnMatcher.missingRequiredFields(fields, suggested),
                sheet.truncated(),
                expiresAt.toString());
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Transactional
    public ImportPreviewDto preview(ImportEntityType type, ImportRunRequestDto request) {
        Organisation org = requireTenantOrg();
        ImportStagedUpload staged = loadStaged(type, org, request.getUploadId());
        byte[] bytes = loadBytes(staged);

        // A preview never writes, whatever the caller asked for, and always reports
        // every bad row in the window: stopping at the first one would defeat the point
        // of previewing.
        ImportOptions options = toOptions(request.getOptions())
                .withDryRun(true)
                .withSkipInvalidRows(true);

        AssetImportResultDto result = executionService.execute(
                type, org, staged.getFilename(), bytes, request.getMapping(), options, PREVIEW_ROW_LIMIT);

        int total = result.getTotalRows();
        int invalid = result.getSkipped();
        int valid = result.getImported() + result.getUpdated();
        return new ImportPreviewDto(
                new ImportPreviewDto.Totals(valid, invalid, total),
                result.getErrors(),
                total,
                staged.getRowCount());
    }

    // ── Staged upload access, for the commit path ─────────────────────────────

    /** Resolves a staged upload for the current tenant, or throws. */
    @Transactional(readOnly = true)
    public ImportStagedUpload requireStagedUpload(ImportEntityType type, Organisation org, UUID uploadId) {
        return loadStaged(type, org, uploadId);
    }

    public byte[] stagedBytes(ImportStagedUpload staged) {
        return loadBytes(staged);
    }

    private ImportStagedUpload loadStaged(ImportEntityType type, Organisation org, UUID uploadId) {
        if (uploadId == null) {
            throw new IllegalArgumentException("uploadId is required");
        }
        ImportStagedUpload staged = stagedUploadRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(uploadId, org)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found or no longer available"));
        if (!staged.getEntityType().equals(type.name())) {
            // The same id under a different type is not this tenant's business either.
            throw new IllegalArgumentException("Upload not found or no longer available");
        }
        if (staged.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("This upload has expired. Upload the file again.");
        }
        return staged;
    }

    private byte[] loadBytes(ImportStagedUpload staged) {
        StoredObject stored = storageService.get(staged.getStorageKey())
                .orElseThrow(() -> new IllegalStateException(
                        "The uploaded file is no longer available. Upload it again."));
        return stored.bytes();
    }

    // ── Mapping presets ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ImportMappingPresetDto> listPresets(ImportEntityType type) {
        Organisation org = requireTenantOrg();
        return presetRepository
                .findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByNameAsc(org, type.name())
                .stream().map(this::toPresetDto).toList();
    }

    @Transactional
    public ImportMappingPresetDto savePreset(ImportEntityType type, ImportMappingPresetDto request) {
        Organisation org = requireTenantOrg();
        String name = request.getName() == null ? null : request.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Preset name is required");
        }
        Map<String, String> mapping = request.getMapping() == null ? Map.of() : request.getMapping();
        java.util.Set<String> known = registry.fields(type).stream()
                .map(ImportFieldDescriptor::name).collect(java.util.stream.Collectors.toSet());
        for (String field : mapping.keySet()) {
            if (!known.contains(field)) {
                throw new IllegalArgumentException("'" + field + "' is not a field of " + type.label());
            }
        }

        ImportMappingPreset preset = presetRepository
                .findByOrganisationAndEntityTypeAndNameIgnoreCaseAndDeletedAtIsNull(org, type.name(), name)
                .orElseGet(ImportMappingPreset::new);
        preset.setOrganisation(org);
        preset.setEntityType(type.name());
        preset.setName(name);
        preset.setMappingJson(writeJson(mapping));
        try {
            return toPresetDto(presetRepository.save(preset));
        } catch (DataIntegrityViolationException clash) {
            // Two saves of the same name raced; the partial unique index caught it.
            throw new IllegalStateException("A mapping named '" + name + "' already exists for this type");
        }
    }

    @Transactional
    public void deletePreset(ImportEntityType type, UUID presetId) {
        Organisation org = requireTenantOrg();
        ImportMappingPreset preset = presetRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(presetId, org)
                .orElseThrow(() -> new IllegalArgumentException("Mapping preset not found"));
        if (!preset.getEntityType().equals(type.name())) {
            throw new IllegalArgumentException("Mapping preset not found");
        }
        preset.setDeletedAt(Instant.now());
        presetRepository.save(preset);
    }

    private ImportMappingPresetDto toPresetDto(ImportMappingPreset preset) {
        ImportMappingPresetDto dto = new ImportMappingPresetDto();
        dto.setId(preset.getId());
        dto.setName(preset.getName());
        dto.setCreatedAt(preset.getCreatedAt());
        dto.setUpdatedAt(preset.getUpdatedAt());
        try {
            dto.setMapping(objectMapper.readValue(preset.getMappingJson(),
                    new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            dto.setMapping(Map.of());
        }
        return dto;
    }

    // ── Options ───────────────────────────────────────────────────────────────

    public ImportOptions toOptions(ImportOptionsDto dto) {
        if (dto == null) return ImportOptions.defaults();
        ImportOptions.DuplicateStrategy strategy = ImportOptions.DuplicateStrategy.SKIP;
        if (dto.getDuplicateStrategy() != null && !dto.getDuplicateStrategy().isBlank()) {
            try {
                strategy = ImportOptions.DuplicateStrategy.valueOf(
                        dto.getDuplicateStrategy().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "duplicateStrategy must be one of SKIP, UPDATE, FAIL");
            }
        }
        return new ImportOptions(strategy,
                Boolean.TRUE.equals(dto.getCreateMissingReferences()),
                Boolean.TRUE.equals(dto.getDryRun()),
                dto.getSkipInvalidRows() == null || dto.getSkipInvalidRows());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise import metadata", e);
        }
    }
}
