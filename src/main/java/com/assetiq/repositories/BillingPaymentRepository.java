package com.assetiq.repositories;

import com.assetiq.models.BillingPayment;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {
    Optional<BillingPayment> findByReferenceAndDeletedAtIsNull(String reference);

    Optional<BillingPayment> findByReferenceAndOrganisationAndDeletedAtIsNull(String reference, Organisation organisation);

    List<BillingPayment> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);
}

