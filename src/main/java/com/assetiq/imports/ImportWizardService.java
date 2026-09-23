package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.ImportAnalysisDto;
import com.assetiq.dto.ImportColumnPlanDto;
import com.assetiq.dto.ImportDetectedColumnDto;
import com.assetiq.dto.ImportEnumFieldDto;
import com.assetiq.dto.ImportMappingPresetDto;
import com.assetiq.dto.ImportOptionsDto;
import com.assetiq.dto.ImportPreviewDto;
import com.assetiq.dto.ImportRunRequestDto;
import com.assetiq.dto.ImportValueSuggestionDto;
import com.assetiq.dto.ImportTypeDto;
import com.assetiq.models.ImportMappingPreset;
import com.assetiq.models.ImportStagedUpload;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.ImportMappingPresetRepository;
import com.assetiq.repositories.ImportStagedUploadRepository;
import com.assetiq.models.CustomFieldDefinition;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.FeatureFlagService;
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

    /** Flag that governs whether a spreadsheet column may become a custom field. */
    public static final String CUSTOM_FIELDS_FLAG = "commercial.governed-custom-fields";

    /** Sample values shown per detected column in the wizard. */
    private static final int SAMPLE_VALUES = 3;

    /**
     * Rows scanned for the distinct values of an enum column.
     *
     * <p>A dropdown per distinct value is only useful while a human can work through it.
     * Scanning the whole file would also make analysing a 50000-row upload a second full
     * pass on the request thread for no extra benefit: a vocabulary that does not appear
     * in the first two thousand rows is rare enough that the importer's own fallback —
     * leave it blank, write a note — is the right answer for it.</p>
     */
    private static final int VALUE_SCAN_ROWS = 2000;

    /** Distinct values offered per enum field. Past this the column is free text. */
    private static final int MAX_DISTINCT_VALUES = 50;

    private final ImportDescriptorRegistry registry;
    private final ImportTemplateGenerator templateGenerator;
    private final SpreadsheetReader spreadsheetReader;
    private final ColumnMatcher columnMatcher;
    private final ImportExecutionService executionService;
    private final ImportStagedUploadRepository stagedUploadRepository;
    private final ImportMappingPresetRepository presetRepository;
    private final FileStorageService storageService;
    private final FeatureFlagService featureFlagService;
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
                               FeatureFlagService featureFlagService,
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
        this.featureFlagService = featureFlagService;
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

        boolean customFieldsAvailable = customFieldsAvailable(type, org);
        return new ImportAnalysisDto(
                uploadId,
                detected,
                fullMapping,
                sheet.rowCount(),
                columnMatcher.unmappedColumns(sheet.headers(), suggested),
                columnMatcher.missingRequiredFields(fields, suggested),
                sheet.truncated(),
                expiresAt.toString(),
                enumFields(fields, suggested, sheet),
                columnPlan(fields, suggested, sheet, customFieldsAvailable),
                customFieldsAvailable,
                customFieldsAvailable ? null : customFieldsUnavailableReason(type),
                true);
    }

    // ── Value mappings, column plan, custom field availability ────────────────

    /**
     * Per enum-typed mapped field: the allowed constants, every distinct raw value the
     * file carries in that column, and the server's suggestion for each.
     *
     * <p>This is the whole answer to "Asset type 'Laptop' is not a valid value". The
     * wizard renders a dropdown per value with the suggestion pre-selected; the user
     * confirms, corrects, or marks a value to be ignored; and what they settle on comes
     * back as {@code valueMappings}. Nothing here can fail an import — a value the user
     * never answered for simply leaves the field blank and earns a note.</p>
     */
    private List<ImportEnumFieldDto> enumFields(List<ImportFieldDescriptor> fields,
                                                Map<String, Integer> suggested,
                                                ParsedSheet sheet) {
        List<ImportEnumFieldDto> result = new ArrayList<>();
        for (ImportFieldDescriptor field : fields) {
            if (field.dataType() != ImportDataType.ENUM) continue;
            Integer column = suggested.get(field.name());
            if (column == null) {
                // Not mapped, so there are no values to translate yet. The allowed list
                // still goes out: the user may map a column in the wizard and the UI
                // needs the constants without another round trip.
                result.add(new ImportEnumFieldDto(field.name(), field.label(), null, null,
                        field.enumValues(), List.of()));
                continue;
            }
            result.add(new ImportEnumFieldDto(field.name(), field.label(), column,
                    headerAt(sheet, column), field.enumValues(),
                    suggestionsFor(field, sheet, column)));
        }
        return result;
    }

    private List<ImportValueSuggestionDto> suggestionsFor(ImportFieldDescriptor field,
                                                          ParsedSheet sheet,
                                                          int column) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> firstSpelling = new LinkedHashMap<>();
        int scanned = Math.min(sheet.rowCount(), VALUE_SCAN_ROWS);
        for (int i = 0; i < scanned; i++) {
            List<String> row = sheet.rows().get(i);
            if (column >= row.size()) continue;
            String raw = row.get(column);
            if (raw == null || raw.isBlank()) continue;
            String key = ImportEnumAliases.normalise(raw);
            if (key.isEmpty()) continue;
            firstSpelling.putIfAbsent(key, raw.trim());
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
        }

        List<ImportValueSuggestionDto> suggestions = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            if (suggestions.size() >= MAX_DISTINCT_VALUES) break;
            String raw = firstSpelling.get(entry.getKey());
            String suggestion = ImportEnumAliases
                    .suggest(field.enumType(), field.enumValues(), raw).orElse(null);
            boolean exact = suggestion != null
                    && ImportEnumAliases.normalise(suggestion).equals(entry.getKey());
            suggestions.add(new ImportValueSuggestionDto(raw, suggestion, exact, entry.getValue()[0]));
        }
        // The values a human most needs to answer first are the ones that cost the most
        // rows if they go unanswered.
        suggestions.sort(java.util.Comparator.comparingInt(ImportValueSuggestionDto::rowCount).reversed());
        return List.copyOf(suggestions);
    }

    /** What the wizard proposes for each column, including the ones no field claimed. */
    private List<ImportColumnPlanDto> columnPlan(List<ImportFieldDescriptor> fields,
                                                 Map<String, Integer> suggested,
                                                 ParsedSheet sheet,
                                                 boolean customFieldsAvailable) {
        Map<Integer, String> fieldByColumn = new LinkedHashMap<>();
        suggested.forEach((field, column) -> { if (column != null) fieldByColumn.put(column, field); });

        List<ImportColumnPlanDto> plan = new ArrayList<>();
        for (int i = 0; i < sheet.columnCount(); i++) {
            String header = sheet.headers().get(i);
            String field = fieldByColumn.get(i);
            if (field != null) {
                plan.add(new ImportColumnPlanDto(i, header, ImportColumnPlanDto.FIELD, field,
                        null, null, false));
                continue;
            }
            if (header == null || header.isBlank()) {
                plan.add(new ImportColumnPlanDto(i, header, ImportColumnPlanDto.IGNORE, null,
                        null, null, false));
                continue;
            }
            String name = CustomFieldDefinition.sanitiseName(header);
            List<String> samples = sheet.sampleValues(i, SAMPLE_VALUES);
            String inferred = CustomFieldDefinitions.infer(samples.isEmpty() ? null : samples.get(0));
            boolean canBeCustom = customFieldsAvailable && !name.isEmpty();
            // Proposed, not decided: a column nothing claimed is likelier to be worth
            // keeping than not, and the user can still set it to ignore.
            plan.add(new ImportColumnPlanDto(i, header,
                    canBeCustom ? ImportColumnPlanDto.CUSTOM_FIELD : ImportColumnPlanDto.IGNORE,
                    null, canBeCustom ? name : null, inferred, canBeCustom));
        }
        return plan;
    }

    /**
     * Whether "create as a custom field" may be offered for this type and tenant.
     *
     * <p>Two gates, and the UI needs to know about both before it draws the dropdown.
     * Only assets have storage for a custom field at all, and custom fields are behind
     * {@code commercial.governed-custom-fields} — a flag that is off by default and
     * granted per organisation. Offering an option that will be refused is worse than
     * not offering it.</p>
     */
    private boolean customFieldsAvailable(ImportEntityType type, Organisation org) {
        if (!registry.handler(type).unmappedColumnsBecomeCustomFields()) return false;
        return featureFlagService.isEnabledFor(CUSTOM_FIELDS_FLAG, org.getId());
    }

    private String customFieldsUnavailableReason(ImportEntityType type) {
        if (!registry.handler(type).unmappedColumnsBecomeCustomFields()) {
            return type.label() + " do not support custom fields, so extra columns can only be ignored.";
        }
        return "Custom fields are not enabled for your organisation, so extra columns can only be"
                + " ignored. Ask your administrator to enable custom fields.";
    }

    private static String headerAt(ParsedSheet sheet, int column) {
        return column >= 0 && column < sheet.headers().size() ? sheet.headers().get(column) : null;
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Transactional
    public ImportPreviewDto preview(ImportEntityType type, ImportRunRequestDto request) {
        Organisation org = requireTenantOrg();
        ImportStagedUpload staged = loadStaged(type, org, request.getUploadId());
        byte[] bytes = loadBytes(staged);

        // A preview never writes, whatever the caller asked for, and always reports
        // every bad row in the window: stopping at the first one would defeat the point
        // of previewing. Everything else -- the mapping, the value translations, the
        // columns kept as custom fields, whether references may be created -- is taken
        // verbatim from the request, because a preview that ran different rules from the
        // commit is the bug this whole path exists to remove.
        ImportOptions options = toOptions(type, org, request.getOptions())
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
                result.getNotes(),
                total,
                staged.getRowCount(),
                result.getOutcome(),
                result.getFatalError(),
                result.getCreatedReferences(),
                result.getCreatedCustomFields());
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

    /**
     * Translates the wire options into the run's options, applying the wizard's
     * defaults.
     *
     * <p>Two defaults differ from the strictest reading, deliberately.
     * {@code createMissingReferences} defaults <b>on</b>: the wizard has already shown
     * the user, in the preview, exactly which records would be created, so defaulting it
     * off only produced the failure this work exists to remove — a row refused for
     * naming a category the tenant does not have, with the fix hidden behind an option
     * the wizard never offered. Sending {@code false} explicitly still gets the strict
     * behaviour. {@code skipInvalidRows} defaults on for the same reason it always has:
     * a handful of bad rows out of 3000 must not stop the other 2990.</p>
     *
     * <p>Custom field columns are validated here rather than at the row: a tenant who
     * cannot use custom fields is told so once, on the request, instead of three thousand
     * times on three thousand rows — and preview and commit refuse it identically.</p>
     */
    @Transactional(readOnly = true)
    public ImportOptions toOptions(ImportEntityType type, ImportOptionsDto dto) {
        return toOptions(type, requireTenantOrg(), dto);
    }

    public ImportOptions toOptions(ImportEntityType type, Organisation org, ImportOptionsDto dto) {
        if (dto == null) return ImportOptions.wizardDefaults();
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
                dto.getCreateMissingReferences() == null || dto.getCreateMissingReferences(),
                Boolean.TRUE.equals(dto.getDryRun()),
                dto.getSkipInvalidRows() == null || dto.getSkipInvalidRows(),
                false,
                customFieldColumns(type, org, dto),
                dto.getValueMappings());
    }

    private java.util.Set<Integer> customFieldColumns(ImportEntityType type, Organisation org,
                                                      ImportOptionsDto dto) {
        List<Integer> requested = dto.getCustomFieldColumns();
        if (requested == null || requested.isEmpty()) return java.util.Set.of();
        if (!customFieldsAvailable(type, org)) {
            throw new IllegalArgumentException(customFieldsUnavailableReason(type));
        }
        if (requested.size() > ImportOptions.MAX_CUSTOM_FIELD_COLUMNS) {
            throw new IllegalArgumentException("At most " + ImportOptions.MAX_CUSTOM_FIELD_COLUMNS
                    + " columns may be kept as custom fields in one import; "
                    + requested.size() + " were selected.");
        }
        java.util.Set<Integer> columns = new java.util.LinkedHashSet<>();
        for (Integer column : requested) {
            if (column == null || column < 0) {
                throw new IllegalArgumentException("customFieldColumns must be 0-based column indices");
            }
            columns.add(column);
        }
        return columns;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise import metadata", e);
        }
    }
}
