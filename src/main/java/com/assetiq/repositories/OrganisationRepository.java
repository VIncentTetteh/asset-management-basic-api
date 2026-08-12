package com.assetiq.repositories;

import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {
    List<Organisation> findAllByDeletedAtIsNull();

    List<Organisation> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

    Optional<Organisation> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Organisation> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    /**
     * Tenant-context gate. Distinct from {@code existsById}, which still matches a
     * soft-deleted organisation and would let a closed account keep making requests.
     */
    boolean existsByIdAndDeletedAtIsNull(UUID id);

    /** Accounts whose retention window has elapsed and which are due for permanent deletion. */
    List<Organisation> findByPurgeAfterBeforeAndDeletedAtIsNotNull(java.time.Instant cutoff);

    /**
     * Permanently delete a tenant, letting the database cascade.
     *
     * <p>Native on purpose. {@code delete(entity)} goes through JPA, and Organisation
     * maps departments/users/assets/roles with {@code CascadeType.ALL}, so Hibernate
     * issues its own DELETEs for those four collections in an order that knows nothing
     * about the other ~90 organisation-scoped tables. Removing app_user rows first
     * trips {@code audit_event.actor_id}, and the purge fails partway through.
     *
     * <p>A single native DELETE hands the whole teardown to the database, where every
     * {@code organisation_id} foreign key is ON DELETE CASCADE and the ordering is the
     * engine's problem rather than a hand-maintained list that rots as tables are added.
     */
    @Modifying
    @Query(value = "DELETE FROM organisation WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);
}
