package com.assetiq.services.impl;

import com.assetiq.controllers.v1.DisposalController;
import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.DisposalMethod;
import com.assetiq.enums.DisposalStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.DisposalRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.DisposalRecordRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Disposals are requested by one user and approved by another")
class DisposalMakerCheckerTest {

    @Mock DisposalRecordRepository disposalRepository;
    @Mock AssetRepository assetRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;

    private DisposalServiceImpl service;
    private Organisation org;
    private Asset asset;
    private User maker;
    private User checker;

    @BeforeEach
    void setUp() {
        service = new DisposalServiceImpl(disposalRepository, assetRepository, organisationRepository,
                userRepository, notificationService, new AssetStateTransitionServiceImpl(assetRepository));
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Forklift");
        asset.setCurrency("GHS");
        asset.setOrganisation(org);
        asset.setStatus(AssetStatus.IN_USE);
        asset.setAssignedUser(new User());
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org)).thenReturn(Optional.of(asset));
        when(disposalRepository.save(any(DisposalRecord.class))).thenAnswer(inv -> {
            DisposalRecord r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            when(disposalRepository.findByIdAndOrganisationAndDeletedAtIsNull(r.getId(), org)).thenReturn(Optional.of(r));
            return r;
        });
        maker = user("maker@example.com");
        checker = user("checker@example.com");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmailAndOrganisationId(email, org.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private void actAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), "n/a", List.of()));
    }

    private DisposalRecordDto request() {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setAssetId(asset.getId());
        dto.setDisposalMethod(DisposalMethod.SALE);
        dto.setDisposalDate(LocalDate.of(2026, 9, 1));
        dto.setSaleValue(new BigDecimal("500.00"));
        actAs(maker);
        return service.createDisposalRecord(dto);
    }

    @Test
    void requestLeavesTheAssetInService() {
        DisposalRecordDto created = request();
        assertThat(created.getStatus()).isEqualTo(DisposalStatus.PENDING_APPROVAL);
        assertThat(created.getRequestedById()).isEqualTo(maker.getId());
        assertThat(created.getApprovedById()).isNull();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
    }

    @Test
    void requesterCannotApproveTheirOwnDisposal() {
        DisposalRecordDto created = request();
        actAs(maker);
        assertThatThrownBy(() -> service.approveDisposal(created.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
    }

    @Test
    void anotherUserApprovesAndTheAssetIsDisposed() {
        DisposalRecordDto created = request();
        actAs(checker);
        DisposalRecordDto approved = service.approveDisposal(created.getId());
        assertThat(approved.getStatus()).isEqualTo(DisposalStatus.APPROVED);
        assertThat(approved.getApprovedById()).isEqualTo(checker.getId());
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DISPOSED);
        assertThat(asset.getAssignedUser()).isNull();

        assertThatThrownBy(() -> service.approveDisposal(created.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saleValueIsLockedAfterApprovalButNotesAreNot() {
        DisposalRecordDto created = request();
        actAs(checker);
        service.approveDisposal(created.getId());

        DisposalRecordDto change = new DisposalRecordDto();
        change.setSaleValue(new BigDecimal("1.00"));
        assertThatThrownBy(() -> service.patchDisposalRecord(created.getId(), change))
                .isInstanceOf(IllegalStateException.class);

        DisposalRecordDto put = new DisposalRecordDto();
        put.setAssetId(asset.getId());
        put.setDisposalMethod(DisposalMethod.SALE);
        put.setDisposalDate(LocalDate.of(2026, 9, 1));
        put.setSaleValue(new BigDecimal("500"));
        put.setReason("Certificate attached");
        put.setComplianceDocumentUrl("https://docs.example.com/cert.pdf");
        DisposalRecordDto updated = service.updateDisposalRecord(created.getId(), put);
        assertThat(updated.getReason()).isEqualTo("Certificate attached");

        put.setSaleValue(new BigDecimal("499"));
        assertThatThrownBy(() -> service.updateDisposalRecord(created.getId(), put))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingValueCanStillBeCorrected() {
        DisposalRecordDto created = request();
        DisposalRecordDto change = new DisposalRecordDto();
        change.setSaleValue(new BigDecimal("450"));
        assertThat(service.patchDisposalRecord(created.getId(), change).getSaleValue()).isEqualByComparingTo("450");
    }

    @Test
    void rejectedDisposalIsClosedAndAssetUntouched() {
        DisposalRecordDto created = request();
        actAs(checker);
        DisposalRecordDto rejected = service.rejectDisposal(created.getId(), "  Still under warranty ");
        assertThat(rejected.getStatus()).isEqualTo(DisposalStatus.REJECTED);
        assertThat(rejected.getRejectedById()).isEqualTo(checker.getId());
        assertThat(rejected.getRejectedByName()).isEqualTo("checker@example.com");
        assertThat(rejected.getRejectionReason()).isEqualTo("Still under warranty");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.IN_USE);
        assertThatThrownBy(() -> service.approveDisposal(created.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectingNeedsAReason() {
        DisposalRecordDto created = request();
        actAs(checker);
        assertThatThrownBy(() -> service.rejectDisposal(created.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.getDisposalById(created.getId()).getStatus()).isEqualTo(DisposalStatus.PENDING_APPROVAL);
    }

    @Test
    void recordsCarryTheAssetAndTheRequesterName() {
        asset.setAssetTag("FL-01");
        maker.setFirstName("Kofi");
        maker.setLastName("Boateng");
        DisposalRecordDto created = request();
        assertThat(created.getAssetName()).isEqualTo("Forklift");
        assertThat(created.getAssetTag()).isEqualTo("FL-01");
        assertThat(created.getRequestedByName()).isEqualTo("Kofi Boateng");
    }

    @Test
    void searchCombinesStatusDateAndAssetFilters() {
        DisposalRecord pendingInRange = record(DisposalStatus.PENDING_APPROVAL, LocalDate.of(2026, 3, 1));
        DisposalRecord approvedInRange = record(DisposalStatus.APPROVED, LocalDate.of(2026, 3, 2));
        DisposalRecord pendingOutOfRange = record(DisposalStatus.PENDING_APPROVAL, LocalDate.of(2025, 1, 1));
        DisposalRecord legacy = record(null, LocalDate.of(2026, 3, 3));
        when(disposalRepository.findByOrganisationAndDeletedAtIsNull(org))
                .thenReturn(Set.of(pendingInRange, approvedInRange, pendingOutOfRange, legacy));

        assertThat(service.searchDisposals(null, LocalDate.of(2026, 1, 1), null, null, DisposalStatus.PENDING_APPROVAL))
                .extracting(DisposalRecordDto::getId).containsExactly(pendingInRange.getId());
        assertThat(service.searchDisposals(asset.getId(), null, null, null, DisposalStatus.APPROVED))
                .extracting(DisposalRecordDto::getId).containsExactly(legacy.getId(), approvedInRange.getId());
        assertThat(service.searchDisposals(UUID.randomUUID(), null, null, null, null)).isEmpty();
    }

    private DisposalRecord record(DisposalStatus status, LocalDate date) {
        DisposalRecord r = new DisposalRecord();
        r.setId(UUID.randomUUID());
        r.setAsset(asset);
        r.setOrganisation(org);
        r.setDisposalMethod(DisposalMethod.SALE);
        r.setDisposalDate(date);
        r.setStatus(status);
        return r;
    }

    @Test
    void secondPendingRequestAndDeletingAnApprovedOneAreRefused() {
        DisposalRecord pending = new DisposalRecord();
        pending.setStatus(DisposalStatus.PENDING_APPROVAL);
        when(disposalRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of(pending));
        assertThatThrownBy(this::request).isInstanceOf(IllegalStateException.class);

        when(disposalRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of());
        DisposalRecordDto created = request();
        actAs(checker);
        service.approveDisposal(created.getId());
        assertThatThrownBy(() -> service.deleteDisposalRecord(created.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvalNeedsFreshMfa() throws Exception {
        assertThat(DisposalController.class.getMethod("approveDisposal", UUID.class)
                .isAnnotationPresent(RequireFreshMfa.class)).isTrue();
    }

    @Test
    void legacyRowsWithoutStatusCountAsApproved() {
        DisposalRecord legacy = new DisposalRecord();
        legacy.setStatus(null);
        assertThat(legacy.isEffective()).isTrue();
    }
}
