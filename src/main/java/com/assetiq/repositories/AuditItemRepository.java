package com.assetiq.repositories;

import com.assetiq.models.AuditItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface AuditItemRepository extends JpaRepository<AuditItem, UUID> {
    Set<AuditItem> findByAuditId(UUID auditId);
    Set<AuditItem> findByAssetId(UUID assetId);
    Set<AuditItem> findByDiscrepancyFlagTrue();
    Set<AuditItem> findByAuditIdAndDiscrepancyFlagTrue(UUID auditId);
}

