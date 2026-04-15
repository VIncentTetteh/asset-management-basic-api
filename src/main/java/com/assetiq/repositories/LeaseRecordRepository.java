package com.assetiq.repositories;

import com.assetiq.enums.LeaseStatus;
import com.assetiq.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface LeaseRecordRepository extends JpaRepository<LeaseRecord, UUID> {
    List<LeaseRecord> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<LeaseRecord> findByAssetAndDeletedAtIsNull(Asset asset);
    Optional<LeaseRecord> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation org);

    @Query("SELECT l FROM LeaseRecord l WHERE l.organisation = :org AND l.status = 'ACTIVE' AND l.endDate <= :cutoff AND l.deletedAt IS NULL")
    List<LeaseRecord> findExpiringSoon(@Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);
}
