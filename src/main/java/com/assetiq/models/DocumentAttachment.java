package com.assetiq.models;

import com.assetiq.enums.AttachmentEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a file uploaded and stored in S3.
 *
 * <p>Intentionally does NOT extend {@link BaseEntity}: the underlying table
 * omits {@code updated_at}, {@code created_by}, and {@code modified_by}
 * because attachments are write-once — delete and re-upload to replace them.
 * All fields are therefore mapped explicitly here.
 */
@Entity
@Table(
        name = "document_attachments",
        indexes = {
                @Index(name = "idx_doc_attachments_entity",
                        columnList = "entity_type, entity_id"),
                @Index(name = "idx_doc_attachments_org",
                        columnList = "organisation_id, deleted_at"),
                @Index(name = "idx_doc_attachments_uploader",
                        columnList = "uploaded_by_id")
        }
)
@Getter
@Setter
public class DocumentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private AttachmentEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
