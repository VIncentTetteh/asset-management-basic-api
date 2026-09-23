package com.assetiq.repositories;

import com.assetiq.enums.InvitationStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.UserInvitation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    /**
     * Redemption lookup. Deliberately not scoped to a tenant: the caller is
     * unauthenticated and holds only the token, and the token is what names the
     * organisation. The hash column is unique, so this can match at most one row.
     */
    Optional<UserInvitation> findByTokenHash(String tokenHash);

    Optional<UserInvitation> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    /** The live invitation for an address in this tenant, if there is one. */
    @Query("""
            select i from UserInvitation i
            where i.organisation = :org
              and lower(i.email) = lower(:email)
              and i.status = com.assetiq.enums.InvitationStatus.PENDING
              and i.deletedAt is null
            """)
    Optional<UserInvitation> findPendingByEmail(@Param("org") Organisation org, @Param("email") String email);

    Page<UserInvitation> findByOrganisationAndDeletedAtIsNull(Organisation organisation, Pageable pageable);

    Page<UserInvitation> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, InvitationStatus status, Pageable pageable);

    /**
     * Pending rows the clock has overtaken — the query behind the EXPIRED filter,
     * which no stored status can answer.
     */
    @Query("""
            select i from UserInvitation i
            where i.organisation = :org and i.deletedAt is null
              and i.status = com.assetiq.enums.InvitationStatus.PENDING
              and i.expiresAt <= :now
            """)
    Page<UserInvitation> findExpired(@Param("org") Organisation org, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select i from UserInvitation i
            where i.organisation = :org and i.deletedAt is null
              and i.status = com.assetiq.enums.InvitationStatus.PENDING
              and i.expiresAt > :now
            """)
    Page<UserInvitation> findLivePending(@Param("org") Organisation org, @Param("now") Instant now, Pageable pageable);

    /**
     * How many sends this tenant has made since {@code since} — the rolling
     * window behind the send-rate limit. Counts resends as well as first sends,
     * because the outbound mail is what is being limited, not the row.
     */
    @Query("""
            select count(i) from UserInvitation i
            where i.organisation = :org and i.lastSentAt is not null and i.lastSentAt >= :since
            """)
    long countSentSince(@Param("org") Organisation org, @Param("since") Instant since);

    /** Live pending invitations, used to project seat usage. */
    @Query("""
            select count(i) from UserInvitation i
            where i.organisation = :org and i.deletedAt is null
              and i.status = com.assetiq.enums.InvitationStatus.PENDING
              and i.expiresAt > :now
            """)
    long countLivePending(@Param("org") Organisation org, @Param("now") Instant now);

    List<UserInvitation> findByOrganisationAndDeletedAtIsNull(Organisation organisation);
}
