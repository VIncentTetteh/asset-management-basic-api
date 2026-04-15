package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import com.assetiq.models.Webhook;
import com.assetiq.models.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByWebhookAndOrganisationOrderByCreatedAtDesc(
            Webhook webhook, Organisation organisation, Pageable pageable);

    Page<WebhookDelivery> findByWebhookAndOrganisationAndStatusOrderByCreatedAtDesc(
            Webhook webhook, Organisation organisation, String status, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndOrganisation(UUID id, Organisation organisation);
}
