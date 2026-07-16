package com.assetiq.dpa;

import com.assetiq.dpa.dto.ConsentRecordDto;
import com.assetiq.dpa.dto.CreateConsentRequest;
import com.assetiq.dpa.dto.CreateDsarRequest;
import com.assetiq.dpa.dto.DsarRequestDto;
import com.assetiq.dpa.model.ConsentRecord;
import com.assetiq.dpa.model.DsarRequest;
import com.assetiq.dpa.repository.ConsentRecordRepository;
import com.assetiq.dpa.repository.DsarRequestRepository;
import com.assetiq.dpa.service.DpaServiceImpl;
import com.assetiq.exceptions.ResourceNotFoundException;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DpaServiceImpl")
class DpaServiceImplTest {

    @Mock ConsentRecordRepository consentRepository;
    @Mock DsarRequestRepository   dsarRepository;
    @Mock UserRepository          userRepository;
    @Mock EmailService            emailService;

    DpaServiceImpl service;
    Organisation   org;
    User           user;

    @BeforeEach
    void setUp() {
        // emailEnabled = false by default (don't test email in unit tests)
        service = new DpaServiceImpl(consentRepository, dsarRepository, userRepository, emailService, false);

        org  = new Organisation();
        org.setId(UUID.randomUUID());
        org.setName("Test Org");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setOrganisation(org);
    }

    // ── Consent: grant ────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordConsent — grants consent and persists a new record")
    void recordConsent_grant_savesRecord() {
        CreateConsentRequest req = new CreateConsentRequest("marketing", true, "127.0.0.1", "TestAgent/1.0");
        when(consentRepository.findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, "marketing"))
                .thenReturn(Optional.empty());

        ConsentRecord saved = stubConsentRecord(user, "marketing", true);
        when(consentRepository.save(any())).thenReturn(saved);

        ConsentRecordDto result = service.recordConsent(org, user, req);

        assertThat(result.purpose()).isEqualTo("marketing");
        assertThat(result.granted()).isTrue();
        verify(consentRepository).save(any(ConsentRecord.class));
    }

    @Test
    @DisplayName("recordConsent — revokes consent and sets revokedAt")
    void recordConsent_revoke_setsRevokedAt() {
        CreateConsentRequest req = new CreateConsentRequest("marketing", false, null, null);
        ConsentRecord existing = stubConsentRecord(user, "marketing", true);
        when(consentRepository.findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, "marketing"))
                .thenReturn(Optional.of(existing));

        ConsentRecord revoked = stubConsentRecord(user, "marketing", false);
        when(consentRepository.save(any())).thenReturn(revoked);

        ConsentRecordDto result = service.recordConsent(org, user, req);

        assertThat(result.granted()).isFalse();
    }

    // ── Consent: revoke ───────────────────────────────────────────────────────

    @Test
    @DisplayName("revokeConsent — throws ResourceNotFoundException when no record exists")
    void revokeConsent_noRecord_throws() {
        when(consentRepository.findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, "analytics"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeConsent(org, user, "analytics"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("analytics");
    }

    @Test
    @DisplayName("revokeConsent — marks existing record as revoked")
    void revokeConsent_existingRecord_revokes() {
        ConsentRecord record = stubConsentRecord(user, "analytics", true);
        when(consentRepository.findByOrganisationAndUserAndPurposeAndDeletedAtIsNull(org, user, "analytics"))
                .thenReturn(Optional.of(record));
        when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConsentRecordDto result = service.revokeConsent(org, user, "analytics");

        assertThat(result.granted()).isFalse();
    }

    // ── hasActiveConsent ──────────────────────────────────────────────────────

    @Test
    @DisplayName("hasActiveConsent — delegates to repository and returns true")
    void hasActiveConsent_returnsTrue() {
        when(consentRepository.existsByOrganisationAndUserAndPurposeAndGrantedTrueAndRevokedAtIsNullAndDeletedAtIsNull(org, user, "marketing"))
                .thenReturn(true);

        assertThat(service.hasActiveConsent(org, user, "marketing")).isTrue();
    }

    // ── DSAR: submit ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitDsarRequest — creates DSAR with PENDING status and 30-day SLA")
    void submitDsar_createsPendingWithSla() {
        CreateDsarRequest req = new CreateDsarRequest("subject@example.com", DsarRequest.RequestType.ACCESS, null);
        DsarRequest saved = stubDsarRequest(DsarRequest.Status.PENDING);
        when(dsarRepository.save(any())).thenReturn(saved);

        DsarRequestDto result = service.submitDsarRequest(org, user, req);

        assertThat(result.status()).isEqualTo(DsarRequest.Status.PENDING);
        assertThat(result.dueAt()).isNotNull();

        ArgumentCaptor<DsarRequest> captor = ArgumentCaptor.forClass(DsarRequest.class);
        verify(dsarRepository).save(captor.capture());
        DsarRequest persisted = captor.getValue();
        assertThat(persisted.getDueAt()).isAfter(Instant.now().plusSeconds(25 * 24 * 3600)); // > 25 days
    }

    @Test
    @DisplayName("submitDsarRequest — does not send email when emailEnabled=false")
    void submitDsar_emailDisabled_noEmailSent() {
        CreateDsarRequest req = new CreateDsarRequest("subject@example.com", DsarRequest.RequestType.ERASURE, null);
        when(dsarRepository.save(any())).thenReturn(stubDsarRequest(DsarRequest.Status.PENDING));

        service.submitDsarRequest(org, user, req);

        verifyNoInteractions(emailService);
    }

    // ── DSAR: update status ───────────────────────────────────────────────────

    @Test
    @DisplayName("updateDsarStatus — transitions to COMPLETED and sets completedAt")
    void updateDsarStatus_completed_setsCompletedAt() {
        DsarRequest record = stubDsarRequest(DsarRequest.Status.IN_PROGRESS);
        UUID id = record.getId();
        when(dsarRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org))
                .thenReturn(Optional.of(record));
        when(dsarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DsarRequestDto result = service.updateDsarStatus(id, org, DsarRequest.Status.COMPLETED, "Done", null);

        assertThat(result.status()).isEqualTo(DsarRequest.Status.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateDsarStatus — throws ResourceNotFoundException for unknown id")
    void updateDsarStatus_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(dsarRepository.findByIdAndOrganisationAndDeletedAtIsNull(unknownId, org))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDsarStatus(unknownId, org, DsarRequest.Status.COMPLETED, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConsentRecord stubConsentRecord(User user, String purpose, boolean granted) {
        ConsentRecord r = new ConsentRecord();
        r.setId(UUID.randomUUID());
        r.setUser(user);
        r.setOrganisation(org);
        r.setPurpose(purpose);
        r.setGranted(granted);
        r.setCreatedAt(Instant.now());
        if (granted) r.setGrantedAt(Instant.now());
        else r.setRevokedAt(Instant.now());
        return r;
    }

    private DsarRequest stubDsarRequest(DsarRequest.Status status) {
        DsarRequest r = new DsarRequest();
        r.setId(UUID.randomUUID());
        r.setOrganisation(org);
        r.setRequesterUser(user);
        r.setRequesterEmail("subject@example.com");
        r.setRequestType(DsarRequest.RequestType.ACCESS);
        r.setStatus(status);
        r.setSubmittedAt(Instant.now());
        r.setDueAt(Instant.now().plusSeconds(30L * 24 * 3600));
        if (status == DsarRequest.Status.COMPLETED) r.setCompletedAt(Instant.now());
        return r;
    }
}
