package com.assetiq.repositories;

import com.assetiq.enums.AttachmentEntityType;
import com.assetiq.models.DocumentAttachment;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, UUID> {

    List<DocumentAttachment> findByEntityTypeAndEntityIdAndDeletedAtIsNull(
            AttachmentEntityType entityType, UUID entityId);

    Optional<DocumentAttachment> findByIdAndOrganisationAndDeletedAtIsNull(
            UUID id, Organisation organisation);

    List<DocumentAttachment> findByOrganisationAndEntityTypeAndEntityIdAndDeletedAtIsNull(
            Organisation organisation, AttachmentEntityType entityType, UUID entityId);
}
