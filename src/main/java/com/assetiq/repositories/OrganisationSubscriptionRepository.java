package com.assetiq.repositories;

import com.assetiq.enums.SubscriptionStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.OrganisationSubscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationSubscriptionRepository extends JpaRepository<OrganisationSubscription, UUID> {
    @EntityGraph(attributePaths = "plan")
    Optional<OrganisationSubscription> findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    List<OrganisationSubscription> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    Optional<OrganisationSubscription> findByPaystackSubscriptionCodeAndDeletedAtIsNull(String paystackSubscriptionCode);

    /** Dunning scan: every subscription currently in a given lifecycle state. */
    @EntityGraph(attributePaths = {"plan", "organisation"})
    List<OrganisationSubscription> findByStatusAndDeletedAtIsNull(SubscriptionStatus status);
}
