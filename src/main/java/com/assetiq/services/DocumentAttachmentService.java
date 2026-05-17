package com.assetiq.services;

import com.assetiq.dto.DocumentAttachmentDto;
import com.assetiq.enums.AttachmentEntityType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentAttachmentService {

    /** Upload a file and attach it to the given entity. Returns the saved attachment with a fresh download URL. */
    DocumentAttachmentDto upload(AttachmentEntityType entityType, UUID entityId, MultipartFile file);

    /** List all non-deleted attachments for an entity. downloadUrl is NOT populated — call getDownloadUrl for that. */
    List<DocumentAttachmentDto> list(AttachmentEntityType entityType, UUID entityId);

    /** Generate a short-lived (15 min) presigned URL for viewing/downloading an attachment. */
    String getDownloadUrl(UUID attachmentId);

    /** Soft-delete the DB record and hard-delete the file from S3. */
    void delete(UUID attachmentId);
}
