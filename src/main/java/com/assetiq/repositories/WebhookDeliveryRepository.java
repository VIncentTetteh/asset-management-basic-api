package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import com.assetiq.models.Webhook;
import com.assetiq.models.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByWebhookAndOrganisationOrderByCreatedAtDesc(
            Webhook webhook, Organisation organisation, Pageable pageable);

    Page<WebhookDelivery> findByWebhookAndOrganisationAndStatusOrderByCreatedAtDesc(
            Webhook webhook, Organisation organisation, String status, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndOrganisation(UUID id, Organisation organisation);

    /**
     * Finds failed deliveries that are eligible for a DLQ retry.
     * Only returns deliveries that:
     * <ol>
     *   <li>Have {@code status = 'failed'}</li>
     *   <li>Were last updated before {@code lastAttemptBefore} (prevents hammering)</li>
     *   <li>Have not yet exceeded {@code maxAttempts} total</li>
     *   <li>Were created after {@code createdAfter} (discard very old events)</li>
     * </ol>
     */
    @Query("SELECT d FROM WebhookDelivery d " +
           "WHERE d.status = 'failed' " +
           "  AND d.updatedAt < :lastAttemptBefore " +
           "  AND d.attempts < :maxAttempts " +
           "  AND d.createdAt > :createdAfter " +
           "  AND d.deletedAt IS NULL")
    List<WebhookDelivery> findRetryableDeliveries(
            @Param("lastAttemptBefore") Instant lastAttemptBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("createdAfter") Instant createdAfter);
}
