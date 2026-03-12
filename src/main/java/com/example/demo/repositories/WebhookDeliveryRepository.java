package com.example.demo.repositories;

import com.example.demo.models.Organisation;
import com.example.demo.models.Webhook;
import com.example.demo.models.WebhookDelivery;
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
