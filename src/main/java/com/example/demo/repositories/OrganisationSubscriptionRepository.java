package com.example.demo.repositories;

import com.example.demo.models.Organisation;
import com.example.demo.models.OrganisationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganisationSubscriptionRepository extends JpaRepository<OrganisationSubscription, UUID> {
    Optional<OrganisationSubscription> findFirstByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    List<OrganisationSubscription> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    Optional<OrganisationSubscription> findByPaystackSubscriptionCodeAndDeletedAtIsNull(String paystackSubscriptionCode);
}

