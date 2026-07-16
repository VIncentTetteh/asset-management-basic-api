package com.assetiq.dpa.repository;

import com.assetiq.dpa.model.ConsentRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    Page<ConsentRecord> findByOrganisationAndDeletedAtIsNull(Organisation org, Pageable pageable);

    List<ConsentRecord> findByUserAndOrganisationAndDeletedAtIsNull(User user, Organisation org);

    Optional<ConsentRecord> findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(
            Organisation org, User user, String purpose);

    boolean existsByOrganisationAndUserAndPurposeAndGrantedTrueAndRevokedAtIsNullAndDeletedAtIsNull(
            Organisation org, User user, String purpose);
}
