package com.assetiq.repositories;

import com.assetiq.models.BillingPayment;
import com.assetiq.models.Organisation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {
    Optional<BillingPayment> findByReferenceAndDeletedAtIsNull(String reference);

    /**
     * Row-locked lookup for applying a payment. The browser verify call and the gateway
     * webhook can arrive together for the same reference; the lock makes the second one
     * wait and then see SUCCESS instead of applying the payment twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select p from BillingPayment p where p.reference = :reference and p.deletedAt is null")
    Optional<BillingPayment> lockByReference(@Param("reference") String reference);

    boolean existsByPaystackTransactionId(Long paystackTransactionId);

    Optional<BillingPayment> findByReferenceAndOrganisationAndDeletedAtIsNull(String reference, Organisation organisation);

    List<BillingPayment> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);
}

