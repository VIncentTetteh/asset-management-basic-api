package com.example.demo.repositories;

import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Optional<AuditEvent> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<AuditEvent> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);
}

