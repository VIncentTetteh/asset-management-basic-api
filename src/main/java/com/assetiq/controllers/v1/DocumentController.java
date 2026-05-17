package com.assetiq.controllers.v1;

import com.assetiq.dto.DocumentAttachmentDto;
import com.assetiq.enums.AttachmentEntityType;
import com.assetiq.services.DocumentAttachmentService;
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
     * Generate a 15-minute presigned download URL for one attachment.
     */
    @GetMapping("/{id}/url")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER')")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable UUID id) {
        String url = documentService.getDownloadUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
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
