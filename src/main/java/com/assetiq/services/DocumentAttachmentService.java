package com.assetiq.services;

import com.assetiq.dto.DocumentAttachmentDto;
import com.assetiq.enums.AttachmentEntityType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentAttachmentService {

    /** Upload a file and attach it to the given entity. Returns the saved attachment with a fresh download URL. */
    DocumentAttachmentDto upload(AttachmentEntityType entityType, UUID entityId, MultipartFile file);

    /** List all non-deleted attachments for an entity. downloadUrl is NOT populated — call getDownloadUrl for that. */
    List<DocumentAttachmentDto> list(AttachmentEntityType entityType, UUID entityId);

    /**
     * Generate a short-lived (15 min) presigned URL for viewing/downloading an attachment.
     * Returns {@code null} when presigned URL generation is unavailable (e.g. S3 disabled);
     * callers should fall back to the {@code /download} streaming endpoint in that case.
     */
    String getDownloadUrl(UUID attachmentId);

    /**
     * Stream the raw file bytes for an attachment.
     * Used as the primary path when S3 is disabled (in-memory storage), and as a
     * redirect target when S3 is enabled (avoids proxying large files through the backend).
     */
    ResponseEntity<byte[]> streamFile(UUID attachmentId);

    /** Soft-delete the DB record and hard-delete the file from S3. */
    void delete(UUID attachmentId);
}
