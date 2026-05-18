package com.assetiq.controllers.v1;

import com.assetiq.dto.DocumentAttachmentDto;
import com.assetiq.enums.AttachmentEntityType;
import com.assetiq.services.DocumentAttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentAttachmentService documentService;

    public DocumentController(DocumentAttachmentService documentService) {
        this.documentService = documentService;
    }

    /**
     * POST /api/v1/documents?entityType=EXPENSE&entityId={uuid}
     * Upload a file and attach it to the given entity.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<DocumentAttachmentDto> upload(
            @RequestParam AttachmentEntityType entityType,
            @RequestParam UUID entityId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(201).body(documentService.upload(entityType, entityId, file));
    }

    /**
     * GET /api/v1/documents?entityType=EXPENSE&entityId={uuid}
     * List all attachments for an entity (no download URLs — fetch separately).
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<List<DocumentAttachmentDto>> list(
            @RequestParam AttachmentEntityType entityType,
            @RequestParam UUID entityId) {
        return ResponseEntity.ok(documentService.list(entityType, entityId));
    }

    /**
     * GET /api/v1/documents/{id}/url
     * Returns a download URL for one attachment.
     * When S3 is enabled, this is a 15-minute presigned URL served directly from S3.
     * When S3 is disabled, this falls back to the backend streaming endpoint URL
     * ({@code /api/v1/documents/{id}/download}) so the frontend can use a single
     * code path regardless of storage configuration.
     */
    @GetMapping("/{id}/url")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @PathVariable UUID id,
            HttpServletRequest request) {
        String presignedUrl = documentService.getDownloadUrl(id);
        if (presignedUrl != null) {
            return ResponseEntity.ok(Map.of("url", presignedUrl));
        }
        // S3 presigning unavailable — return the backend streaming endpoint as fallback.
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                   ? "" : ":" + request.getServerPort());
        String streamUrl = baseUrl + "/api/v1/documents/" + id + "/download";
        return ResponseEntity.ok(Map.of("url", streamUrl));
    }

    /**
     * GET /api/v1/documents/{id}/download
     * Streams raw file bytes for an attachment.
     * Primary use-case: in-memory storage (S3 disabled) where no presigned URL exists.
     * Also works with S3 — fetches the object and streams it — though clients should
     * prefer the presigned URL from {@code /url} to avoid proxying large files.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return documentService.streamFile(id);
    }

    /**
     * DELETE /api/v1/documents/{id}
     * Soft-delete the record and remove the file from storage.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
