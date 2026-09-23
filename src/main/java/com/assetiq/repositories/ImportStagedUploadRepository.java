package com.assetiq.repositories;

import com.assetiq.models.ImportStagedUpload;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportStagedUploadRepository extends JpaRepository<ImportStagedUpload, UUID> {

    /**
     * The only way the service reads an upload. The organisation predicate is what
     * stops one tenant acting on another's staged file, even given its id.
     */
    Optional<ImportStagedUpload> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    /** Expired uploads awaiting cleanup. Deliberately not tenant-scoped: it runs as a job. */
    List<ImportStagedUpload> findTop200ByExpiresAtBeforeAndDeletedAtIsNull(Instant cutoff);
}
