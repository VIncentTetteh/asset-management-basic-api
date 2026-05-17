package com.assetiq.services.impl;

import com.assetiq.dto.DocumentAttachmentDto;
import com.assetiq.enums.AttachmentEntityType;
import com.assetiq.models.DocumentAttachment;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.DocumentAttachmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.DocumentAttachmentService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.storage.OrgAwareStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentAttachmentServiceImpl extends TenantAwareService implements DocumentAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAttachmentServiceImpl.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv"
    );

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB

    private final DocumentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final OrgAwareStorageService orgAwareStorageService;

    public DocumentAttachmentServiceImpl(OrganisationRepository organisationRepository,
                                         DocumentAttachmentRepository attachmentRepository,
                                         UserRepository userRepository,
                                         OrgAwareStorageService orgAwareStorageService) {
        super(organisationRepository);
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.orgAwareStorageService = orgAwareStorageService;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Override
    public DocumentAttachmentDto upload(AttachmentEntityType entityType, UUID entityId, MultipartFile file) {
        // 1. Validate file is not empty
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }

        // 2. Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType +
                    ". Allowed types: PDF, images (JPEG/PNG/GIF/WEBP), Word, Excel, plain text, CSV.");
        }

        // 3. Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds the 25 MB limit.");
        }

        // 4. Resolve current org and user
        Organisation org = requireTenantOrg();
        User currentUser = resolveCurrentUser(org);

        // 5. Build storage key with sanitised filename
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String sanitised = sanitiseFilename(originalName);
        String storageKey = "attachments/" +
                entityType.name().toLowerCase() + "/" +
                entityId + "/" +
                UUID.randomUUID() + "-" + sanitised;

        // 6. Upload to storage
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file bytes: " + e.getMessage(), e);
        }

        Map<String, String> metadata = Map.of(
                "entityType", entityType.name(),
                "entityId", entityId.toString()
        );

        orgAwareStorageService.store(storageKey, bytes, contentType, originalName, metadata);
        log.debug("[DocumentAttachment] Stored file at key={} for entityType={} entityId={}", storageKey, entityType, entityId);

        // 7. Persist the attachment record
        DocumentAttachment attachment = new DocumentAttachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setOriginalName(originalName);
        attachment.setContentType(contentType);
        attachment.setStorageKey(storageKey);
        attachment.setFileSize(file.getSize());
        attachment.setUploadedBy(currentUser);
        attachment.setOrganisation(org);

        DocumentAttachment saved = attachmentRepository.save(attachment);

        // 8. Return DTO with a fresh download URL
        DocumentAttachmentDto dto = toDto(saved);
        dto.setDownloadUrl(getDownloadUrl(saved.getId()));
        return dto;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAttachmentDto> list(AttachmentEntityType entityType, UUID entityId) {
        Organisation org = requireTenantOrg();
        return attachmentRepository
                .findByOrganisationAndEntityTypeAndEntityIdAndDeletedAtIsNull(org, entityType, entityId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ── Get download URL ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID attachmentId) {
        Organisation org = requireTenantOrg();
        DocumentAttachment attachment = attachmentRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(attachmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        return orgAwareStorageService
                .createPresignedGetUrl(
                        attachment.getStorageKey(),
                        attachment.getOriginalName(),
                        attachment.getContentType(),
                        Duration.ofMinutes(15))
                .orElseThrow(() -> new IllegalStateException(
                        "Could not generate download URL for attachment: " + attachmentId));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void delete(UUID attachmentId) {
        Organisation org = requireTenantOrg();
        DocumentAttachment attachment = attachmentRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(attachmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        // Soft-delete first — preserve the record even if S3 deletion fails
        attachment.setDeletedAt(Instant.now());
        attachmentRepository.save(attachment);

        // Hard-delete from storage after the DB record is safely committed
        try {
            orgAwareStorageService.delete(attachment.getStorageKey());
            log.debug("[DocumentAttachment] Hard-deleted from storage key={}", attachment.getStorageKey());
        } catch (Exception e) {
            // Log and continue — the record is already soft-deleted; S3 cleanup can be retried separately
            log.warn("[DocumentAttachment] Soft-deleted id={} but S3 delete failed for key={}: {}",
                    attachmentId, attachment.getStorageKey(), e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Strips path separators, replaces spaces with underscores, and keeps only
     * alphanumeric characters, dots, and hyphens to produce a safe S3 key segment.
     */
    private String sanitiseFilename(String filename) {
        // Remove any path components (e.g. ../../evil)
        String name = filename.replaceAll("[/\\\\]", "");
        // Replace spaces with underscores
        name = name.replace(' ', '_');
        // Remove any character that isn't alphanumeric, a dot, hyphen, or underscore
        name = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        return name.isEmpty() ? "file" : name;
    }

    /**
     * Resolves the authenticated user within the given organisation.
     * Mirrors the pattern used in {@link ExpenseServiceImpl}.
     */
    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId())
                    .orElseThrow(() -> new AccessDeniedException(
                            "Authenticated user not found in organisation"));
        }
        throw new AccessDeniedException("No authenticated user in security context");
    }

    private DocumentAttachmentDto toDto(DocumentAttachment a) {
        return DocumentAttachmentDto.builder()
                .id(a.getId())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .originalName(a.getOriginalName())
                .contentType(a.getContentType())
                .fileSize(a.getFileSize())
                .uploadedByName(a.getUploadedBy() != null
                        ? a.getUploadedBy().getFirstName() + " " + a.getUploadedBy().getLastName()
                        : null)
                .createdAt(a.getCreatedAt())
                .build();
    }
}
