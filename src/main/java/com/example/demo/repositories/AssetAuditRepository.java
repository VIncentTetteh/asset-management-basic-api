package com.example.demo.repositories;

import com.example.demo.models.AssetAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AssetAuditRepository extends JpaRepository<AssetAudit, UUID> {
    Set<AssetAudit> findByOrganisationId(UUID organisationId);
    Set<AssetAudit> findByDepartmentId(UUID departmentId);
    Set<AssetAudit> findByAuditDateBetween(LocalDate startDate, LocalDate endDate);
    Set<AssetAudit> findByConductedById(UUID userId);
}

