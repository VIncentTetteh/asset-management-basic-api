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
import com.assetiq.storage.StoredObject;
import com.assetiq.storage.UploadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentAttachmentServiceImpl extends TenantAwareService implements DocumentAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAttachmentServiceImpl.class);

    /**
     * Size, content-type allow-list and byte-signature checks all live in
     * {@link UploadValidator}, so every entry point that accepts a file answers
     * them the same way.
     */
    private final UploadValidator uploadValidator;

    private final DocumentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final OrgAwareStorageService orgAwareStorageService;

    public DocumentAttachmentServiceImpl(OrganisationRepository organisationRepository,
                                         DocumentAttachmentRepository attachmentRepository,
                                         UserRepository userRepository,
                                         OrgAwareStorageService orgAwareStorageService,
                                         UploadValidator uploadValidator) {
        super(organisationRepository);
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.orgAwareStorageService = orgAwareStorageService;
        this.uploadValidator = uploadValidator;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Override
    public DocumentAttachmentDto upload(AttachmentEntityType entityType, UUID entityId, MultipartFile file) {
        // 1. Size, content-type allow-list, and a byte-signature check that the
        //    content is what the client's Content-Type claims. Throws with a
        //    caller-safe message on any of them.
        UploadValidator.ValidatedUpload validated = uploadValidator.validate(file);

        // 2. Resolve current org and user
        Organisation org = requireTenantOrg();
        User currentUser = resolveCurrentUser(org);

        // 3. Build the storage key. Every path segment is generated: the tenant
        //    id, the entity, and a fresh UUID. The sanitised original name is the
        //    trailing segment for legibility only, so a hostile filename can
        //    neither collide with another object nor place one outside the
        //    tenant's prefix. Prefixing by organisation mirrors what reports and
        //    imports already do, and makes tenant isolation visible in the object
        //    layout as well as in the queries below.
        String storageKey = "attachments/" + org.getId() + "/" +
                entityType.name().toLowerCase() + "/" +
                entityId + "/" +
                UUID.randomUUID() + "-" + validated.sanitisedFilename();

        Map<String, String> metadata = Map.of(
                "entityType", entityType.name(),
                "entityId", entityId.toString(),
                "organisationId", org.getId().toString()
        );

        orgAwareStorageService.store(storageKey, validated.bytes(), validated.contentType(),
                                     validated.sanitisedFilename(), metadata);
        log.debug("[DocumentAttachment] Stored file at key={} for entityType={} entityId={}",
                  storageKey, entityType, entityId);

        // 4. Persist the attachment record. originalName is the sanitised name:
        //    it is echoed back to the UI and put in a Content-Disposition header,
        //    so storing the raw client string would just move the problem.
        DocumentAttachment attachment = new DocumentAttachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setOriginalName(validated.sanitisedFilename());
        attachment.setContentType(validated.contentType());
        attachment.setStorageKey(storageKey);
        attachment.setFileSize((long) validated.bytes().length);
        attachment.setUploadedBy(currentUser);
        attachment.setOrganisation(org);

        DocumentAttachment saved = attachmentRepository.save(attachment);

        // 5. Return DTO with a fresh download URL
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

        // Returns null when presigned URL is unavailable (e.g. S3 disabled);
        // the controller falls back to the /download streaming endpoint in that case.
        return orgAwareStorageService
                .createPresignedGetUrl(
                        attachment.getStorageKey(),
                        attachment.getOriginalName(),
                        attachment.getContentType(),
                        Duration.ofMinutes(15))
                .orElse(null);
    }

    // ── Stream file bytes ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> streamFile(UUID attachmentId) {
        Organisation org = requireTenantOrg();
        DocumentAttachment attachment = attachmentRepository
                .findByIdAndOrganisationAndDeletedAtIsNull(attachmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

        StoredObject stored = orgAwareStorageService.get(attachment.getStorageKey())
                .orElseThrow(() -> new IllegalStateException(
                        "File not found in storage for attachment: " + attachmentId));

        // Served as a download, never as a page. `attachment` stops the browser
        // rendering the file in the application's own origin, and `nosniff` stops
        // it second-guessing the declared type and rendering it anyway. Together
        // with the upload allow-list, that is what keeps an uploaded file from
        // ever executing as part of AssetIQ.
        return ResponseEntity.ok()
                .header("Content-Type", attachment.getContentType())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Content-Disposition",
                        "attachment; filename=\"" + attachment.getOriginalName() + "\"")
                .header("Content-Length", String.valueOf(stored.bytes().length))
                .header("Cache-Control", "no-store")
                .body(stored.bytes());
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
