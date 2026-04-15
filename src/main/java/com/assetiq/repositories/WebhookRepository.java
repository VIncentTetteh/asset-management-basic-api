package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import com.assetiq.models.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Optional<Webhook> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    /** Returns active webhooks subscribed to the given event name (contains check). */
    List<Webhook> findByOrganisationAndActiveTrueAndDeletedAtIsNull(Organisation organisation);
}
