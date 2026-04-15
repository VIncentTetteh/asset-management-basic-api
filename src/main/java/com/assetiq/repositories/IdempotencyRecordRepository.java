package com.assetiq.repositories;

import com.assetiq.models.IdempotencyRecord;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByOrganisationAndOperationAndIdempotencyKeyAndDeletedAtIsNull(
            Organisation organisation,
            String operation,
            String idempotencyKey
    );
}

