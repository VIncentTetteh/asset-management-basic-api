package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetImportJobDto;
import com.assetiq.dto.ImportAnalysisDto;
import com.assetiq.dto.ImportMappingPresetDto;
import com.assetiq.dto.ImportPreviewDto;
import com.assetiq.dto.ImportRunRequestDto;
import com.assetiq.dto.ImportTypeDto;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportWizardService;
import com.assetiq.services.AssetImportJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The mapping-driven import wizard.
 *
 * <p>Five steps, in order: pick a type, download a template or upload your own file,
 * check the suggested mapping, preview it, commit it. Every response is derived from
 * the entity type's field descriptors, so the wizard has nothing hard-coded about any
 * particular record type — adding a ninth type makes it appear here with no change to
 * this controller.</p>
 *
 * <p>Authorisation is <b>per entity type</b>, not one blanket import permission: see
 * {@link com.assetiq.imports.ImportPermissions}. Somebody trusted with the supplier
 * list does not thereby get to create three thousand employee records.</p>
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportsController {

    private final ImportWizardService wizardService;
    private final AssetImportJobService importJobService;

    public ImportsController(ImportWizardService wizardService, AssetImportJobService importJobService) {
        this.wizardService = wizardService;
        this.importJobService = importJobService;
    }

    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ImportTypeDto>> listTypes() {
        return ResponseEntity.ok(wizardService.listTypes());
    }

    @GetMapping("/{type}/fields")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<List<ImportFieldDescriptor>> fields(@PathVariable String type) {
        return ResponseEntity.ok(wizardService.fields(resolve(type)));
    }

    /**
     * The template, built from the same descriptors the importer validates against.
     * The .xlsx carries a header row, one example row, and a second sheet documenting
     * every column.
     */
    @GetMapping("/{type}/template")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<byte[]> template(@PathVariable String type,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        ImportWizardService.Template template = wizardService.template(resolve(type), format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, template.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + template.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(template.bytes());
    }

    /** Stage an upload and suggest a mapping. Writes nothing to the tenant's records. */
    @PostMapping(value = "/{type}/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<ImportAnalysisDto> analyse(@PathVariable String type,
                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(wizardService.analyse(resolve(type), file));
    }

    /** Validate the mapping against the file without writing anything. */
    @PostMapping("/{type}/preview")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<ImportPreviewDto> preview(@PathVariable String type,
                                                    @Valid @RequestBody ImportRunRequestDto request) {
        return ResponseEntity.ok(wizardService.preview(resolve(type), request));
    }

    /**
     * Commit the staged upload as an async job. Poll it at
     * {@code GET /api/v1/import-jobs/{jobId}} — the same endpoint the asset import has
     * always used.
     */
    @PostMapping("/{type}/commit")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<AssetImportJobDto> commit(
            @PathVariable String type,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ImportRunRequestDto request) {
        ImportEntityType entityType = resolve(type);
        AssetImportJobDto job = importJobService.createMappedImportJob(
                entityType,
                request.getUploadId(),
                request.getMapping(),
                wizardService.toOptions(entityType, request.getOptions()),
                idempotencyKey);
        return ResponseEntity.accepted().body(job);
    }

    // ── Mapping presets ───────────────────────────────────────────────────────

    @GetMapping("/{type}/mappings")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<List<ImportMappingPresetDto>> listMappings(@PathVariable String type) {
        return ResponseEntity.ok(wizardService.listPresets(resolve(type)));
    }

    @PostMapping("/{type}/mappings")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<ImportMappingPresetDto> saveMapping(
            @PathVariable String type,
            @Valid @RequestBody ImportMappingPresetDto request) {
        return ResponseEntity.ok(wizardService.savePreset(resolve(type), request));
    }

    @DeleteMapping("/{type}/mappings/{presetId}")
    @PreAuthorize("@importPermissions.canImport(#type)")
    public ResponseEntity<Void> deleteMapping(@PathVariable String type, @PathVariable UUID presetId) {
        wizardService.deletePreset(resolve(type), presetId);
        return ResponseEntity.noContent().build();
    }

    private ImportEntityType resolve(String type) {
        return ImportEntityType.fromSlug(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + type + "' is not an importable record type"));
    }
}
