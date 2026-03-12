package com.example.demo.repositories;

import com.example.demo.models.Organisation;
import com.example.demo.models.Webhook;
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
