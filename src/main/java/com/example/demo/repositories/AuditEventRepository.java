package com.example.demo.repositories;

import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Optional<AuditEvent> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<AuditEvent> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    @Query("SELECT e FROM AuditEvent e WHERE e.organisation = :org " +
           "AND LOWER(e.path) LIKE LOWER(CONCAT('%', :assetId, '%')) " +
           "AND e.deletedAt IS NULL ORDER BY e.createdAt DESC")
    List<AuditEvent> findByOrganisationAndAssetIdInPath(@Param("org") Organisation org, @Param("assetId") String assetId);
}

