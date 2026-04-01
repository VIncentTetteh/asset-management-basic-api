package com.example.demo.repositories;

import com.example.demo.models.IdempotencyRecord;
import com.example.demo.models.Organisation;
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

