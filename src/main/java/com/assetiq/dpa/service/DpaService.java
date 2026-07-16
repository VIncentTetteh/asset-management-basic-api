package com.assetiq.dpa.service;

import com.assetiq.dpa.dto.ConsentRecordDto;
import com.assetiq.dpa.dto.CreateConsentRequest;
import com.assetiq.dpa.dto.CreateDsarRequest;
import com.assetiq.dpa.dto.DsarRequestDto;
import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Ghana Data Protection Act 2012 compliance service.
 * Handles consent management and Data Subject Access Requests (DSAR).
 */
public interface DpaService {

    // ── Consent ──────────────────────────────────────────────────────────────

    /** Record or update a consent decision for a given purpose. */
    ConsentRecordDto recordConsent(Organisation org, User user, CreateConsentRequest request);

    /** Revoke an existing active consent for a given purpose. */
    ConsentRecordDto revokeConsent(Organisation org, User user, String purpose);

    /** List all consent records for the organisation (admin view). */
    Page<ConsentRecordDto> listConsents(Organisation org, Pageable pageable);

    /** Check whether the user has an active, non-revoked consent for the given purpose. */
    boolean hasActiveConsent(Organisation org, User user, String purpose);

    // ── DSAR ─────────────────────────────────────────────────────────────────

    /** Submit a new Data Subject Access Request. SLA due date is set to 30 days from now. */
    DsarRequestDto submitDsarRequest(Organisation org, User requester, CreateDsarRequest request);

    /** List DSARs, optionally filtered by status. */
    Page<DsarRequestDto> listDsarRequests(Organisation org, DsarRequest.Status status, Pageable pageable);

    /** Get a single DSAR by ID, scoped to the organisation. */
    DsarRequestDto getDsarRequest(UUID id, Organisation org);

    /** Update a DSAR's status, response summary, and/or assigned handler. */
    DsarRequestDto updateDsarStatus(UUID id, Organisation org, DsarRequest.Status newStatus,
                                    String responseSummary, UUID assignedToUserId);
}
