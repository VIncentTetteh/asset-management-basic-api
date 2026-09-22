package com.assetiq.dpa.service;

import com.assetiq.dpa.dto.ConsentRecordDto;
import com.assetiq.dpa.dto.CreateConsentRequest;
import com.assetiq.dpa.dto.CreateDsarRequest;
import com.assetiq.dpa.dto.DsarRequestDto;
import com.assetiq.dpa.model.ConsentRecord;
import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.dpa.repository.ConsentRecordRepository;
import com.assetiq.dpa.repository.DsarRequestRepository;
import com.assetiq.exceptions.ResourceNotFoundException;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DpaServiceImpl implements DpaService {

    /** Ghana DPA 2012, s.27 — respond to a DSAR within 30 days. */
    private static final int DSAR_SLA_DAYS = 30;

    private final ConsentRecordRepository consentRepository;
    private final DsarRequestRepository   dsarRepository;
    private final UserRepository          userRepository;
    private final EmailService            emailService;
    private final boolean                 emailEnabled;

    public DpaServiceImpl(ConsentRecordRepository consentRepository,
                          DsarRequestRepository   dsarRepository,
                          UserRepository          userRepository,
                          EmailService            emailService,
                          @org.springframework.beans.factory.annotation.Value("${app.email.enabled:false}") boolean emailEnabled) {
        this.consentRepository = consentRepository;
        this.dsarRepository    = dsarRepository;
        this.userRepository    = userRepository;
        this.emailService      = emailService;
        this.emailEnabled      = emailEnabled;
    }

    // ── Consent ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ConsentRecordDto recordConsent(Organisation org, User user, CreateConsentRequest req,
                                          String clientIp, String userAgent) {
        ConsentRecord record = consentRepository
                .findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, req.purpose())
                .orElseGet(ConsentRecord::new);

        record.setOrganisation(org);
        record.setUser(user);
        record.setPurpose(req.purpose());
        record.setGranted(req.granted());
        record.setIpAddress(truncate(clientIp, 50));
        record.setUserAgent(truncate(userAgent, 500));

        if (req.granted()) {
            record.setGrantedAt(Instant.now());
            record.setRevokedAt(null);
        } else {
            record.setRevokedAt(Instant.now());
        }

        ConsentRecord saved = consentRepository.save(record);
        log.info("[DPA] Consent {} — purpose='{}' user={} org={}",
                req.granted() ? "granted" : "revoked", req.purpose(), user.getId(), org.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ConsentRecordDto revokeConsent(Organisation org, User user, String purpose) {
        ConsentRecord record = consentRepository
                .findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, purpose)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Consent record not found for purpose: " + purpose));

        record.setGranted(false);
        record.setRevokedAt(Instant.now());
        log.info("[DPA] Consent revoked — purpose='{}' user={} org={}", purpose, user.getId(), org.getId());
        return toDto(consentRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConsentRecordDto> listConsents(Organisation org, Pageable pageable) {
        return consentRepository.findByOrganisationAndDeletedAtIsNull(org, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(Organisation org, User user, String purpose) {
        return consentRepository
                .existsByOrganisationAndUserAndPurposeAndGrantedTrueAndRevokedAtIsNullAndDeletedAtIsNull(
                        org, user, purpose);
    }

    // ── DSAR ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DsarRequestDto submitDsarRequest(Organisation org, User requester, CreateDsarRequest req) {
        DsarRequest dsar = new DsarRequest();
        dsar.setOrganisation(org);
        dsar.setRequesterUser(requester);
        dsar.setRequesterEmail(req.requesterEmail());
        dsar.setRequestType(req.requestType());
        dsar.setStatus(DsarRequest.Status.PENDING);
        dsar.setSubmittedAt(Instant.now());
        dsar.setDueAt(Instant.now().plus(DSAR_SLA_DAYS, ChronoUnit.DAYS));
        dsar.setNotes(req.notes());

        DsarRequest saved = dsarRepository.save(dsar);
        log.info("[DPA] DSAR submitted — type={} requester={} org={} due={}",
                req.requestType(), req.requesterEmail(), org.getId(), saved.getDueAt());

        // Send acknowledgement email (Ghana DPA 2012 — confirm receipt to data subject)
        if (emailEnabled) {
            try {
                Map<String, Object> model = new java.util.HashMap<>();
                model.put("requesterEmail", saved.getRequesterEmail());
                model.put("requestType",    saved.getRequestType().name());
                model.put("submittedAt",    saved.getSubmittedAt().toString());
                model.put("dueAt",          saved.getDueAt().toString());
                model.put("dsarId",         saved.getId().toString());
                emailService.sendTemplate(
                        saved.getRequesterEmail(),
                        "Your Data Subject Request Has Been Received — AssetIQ",
                        "dsar-acknowledgement",
                        model);
                log.info("[DPA] DSAR acknowledgement email sent to {}", saved.getRequesterEmail());
            } catch (Exception e) {
                // Email failure must never block the DSAR record being created
                log.warn("[DPA] Failed to send DSAR acknowledgement email to {}: {}",
                         saved.getRequesterEmail(), e.getMessage());
            }
        }

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DsarRequestDto> listDsarRequests(Organisation org, DsarRequest.Status status, Pageable pageable) {
        if (status != null) {
            return dsarRepository.findByOrganisationAndStatusAndDeletedAtIsNull(org, status, pageable)
                    .map(this::toDto);
        }
        return dsarRepository.findByOrganisationAndDeletedAtIsNull(org, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DsarRequestDto getDsarRequest(UUID id, Organisation org) {
        return toDto(dsarRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("DSAR request not found: " + id)));
    }

    @Override
    @Transactional
    public DsarRequestDto updateDsarStatus(UUID id, Organisation org, DsarRequest.Status newStatus,
                                           String responseSummary, UUID assignedToUserId, boolean clearAssignee) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }
        DsarRequest dsar = dsarRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("DSAR request not found: " + id));

        DsarRequest.Status current = dsar.getStatus();
        boolean closed = current == DsarRequest.Status.COMPLETED || current == DsarRequest.Status.REJECTED;
        if (closed && newStatus != current) {
            // A completed or rejected request is the organisation's record of its
            // statutory response; it is not reopened.
            throw new IllegalStateException("A " + current + " DSAR request cannot be moved to " + newStatus);
        }
        dsar.setStatus(newStatus);
        if (responseSummary != null) dsar.setResponseSummary(responseSummary.isBlank() ? null : responseSummary.trim());
        boolean closing = (newStatus == DsarRequest.Status.COMPLETED || newStatus == DsarRequest.Status.REJECTED)
                && newStatus != current;
        if (closing) dsar.setCompletedAt(Instant.now());
        if (clearAssignee) {
            dsar.setAssignedTo(null);
        } else if (assignedToUserId != null) {
            // Only a user of the same organisation (was findById: any tenant's user).
            dsar.setAssignedTo(userRepository.findByIdAndOrganisation(assignedToUserId, org)
                    .orElseThrow(() -> new IllegalArgumentException("Assignee not found in your organisation")));
        }

        log.info("[DPA] DSAR {} updated to status={} org={}", id, newStatus, org.getId());
        return toDto(dsarRepository.save(dsar));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ConsentRecordDto toDto(ConsentRecord r) {
        return new ConsentRecordDto(
                r.getId(), r.getUser().getId(), r.getPurpose(),
                r.isGranted(), r.getGrantedAt(), r.getRevokedAt(), r.getCreatedAt());
    }

    private DsarRequestDto toDto(DsarRequest r) {
        return new DsarRequestDto(
                r.getId(), r.getRequesterEmail(), r.getRequestType(), r.getStatus(),
                r.getSubmittedAt(), r.getDueAt(), r.getCompletedAt(),
                r.getAssignedTo() != null ? r.getAssignedTo().getId() : null,
                r.getNotes(), r.getResponseSummary());
    }
}
