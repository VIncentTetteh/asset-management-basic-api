package com.assetiq.services.impl;

import com.assetiq.controllers.v1.AssetTransferController;
import com.assetiq.dto.AssetTransferDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.TransferStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
@DisplayName("Asset transfer state machine")
class AssetTransferWorkflowTest {

    @Mock AssetTransferRepository transferRepository;
    @Mock AssetRepository assetRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock LocationRepository locationRepository;
    @Mock UserRepository userRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock NotificationService notificationService;

    private AssetTransferServiceImpl service;
    private Organisation org;
    private Asset asset;
    private Department from;
    private Department to;
    private User requester;
    private User approver;

    @BeforeEach
    void setUp() {
        service = new AssetTransferServiceImpl(transferRepository, assetRepository, departmentRepository,
                locationRepository, userRepository, organisationRepository, notificationService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("Laptop");
        asset.setOrganisation(org);
        asset.setStatus(AssetStatus.IN_USE);
        from = dept("Finance");
        to = dept("IT");
        requester = user("maker@example.com");
        approver = user("checker@example.com");
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org)).thenReturn(Optional.of(asset));
        when(transferRepository.save(any(AssetTransfer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Department dept(String name) {
        Department d = new Department();
        d.setId(UUID.randomUUID());
        d.setName(name);
        when(departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(d.getId(), org)).thenReturn(Optional.of(d));
        return d;
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        when(userRepository.findByEmailAndOrganisationId(email, org.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private void actAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), "n/a", List.of()));
    }

    private AssetTransfer transfer(TransferStatus status) {
        AssetTransfer t = new AssetTransfer();
        t.setId(UUID.randomUUID());
        t.setAsset(asset);
        t.setFromDepartment(from);
        t.setToDepartment(to);
        t.setRequestedBy(requester);
        t.setOrganisation(org);
        t.setStatus(status);
        when(transferRepository.findByIdAndDeletedAtIsNull(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"APPROVED", "COMPLETED", "CANCELLED", "REJECTED"})
    void approveOnlyFromRequested(TransferStatus status) {
        AssetTransfer t = transfer(status);
        actAs(approver);
        assertThatThrownBy(() -> service.approveTransfer(t.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requesterCannotApproveOwnTransfer() {
        AssetTransfer t = transfer(TransferStatus.REQUESTED);
        actAs(requester);
        assertThatThrownBy(() -> service.approveTransfer(t.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectMarksRejectedAndIsRefusedOnceCompleted() {
        AssetTransfer t = transfer(TransferStatus.REQUESTED);
        actAs(approver);
        assertThat(service.rejectTransfer(t.getId()).getStatus()).isEqualTo(TransferStatus.REJECTED);

        AssetTransfer done = transfer(TransferStatus.COMPLETED);
        assertThatThrownBy(() -> service.rejectTransfer(done.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeRecordsCompleterAndMovesAsset() {
        AssetTransfer t = transfer(TransferStatus.APPROVED);
        actAs(approver);
        AssetTransferDto out = service.completeTransfer(t.getId());
        assertThat(out.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(out.getCompletedById()).isEqualTo(approver.getId());
        assertThat(asset.getDepartment()).isEqualTo(to);
    }

    @Test
    void completeRequiresApproval() {
        AssetTransfer t = transfer(TransferStatus.REQUESTED);
        actAs(approver);
        assertThatThrownBy(() -> service.completeTransfer(t.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedTransferCannotBeDeleted() {
        AssetTransfer t = transfer(TransferStatus.COMPLETED);
        assertThatThrownBy(() -> service.deleteTransfer(t.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createRejectsSameDepartmentAndSecondOpenTransfer() {
        actAs(requester);
        AssetTransferDto same = new AssetTransferDto();
        same.setAssetId(asset.getId());
        same.setFromDepartmentId(from.getId());
        same.setToDepartmentId(from.getId());
        assertThatThrownBy(() -> service.createTransferRequest(same)).isInstanceOf(IllegalArgumentException.class);

        AssetTransfer open = transfer(TransferStatus.APPROVED);
        when(transferRepository.findByAssetIdAndDeletedAtIsNull(asset.getId())).thenReturn(Set.of(open));
        AssetTransferDto dto = new AssetTransferDto();
        dto.setAssetId(asset.getId());
        dto.setFromDepartmentId(from.getId());
        dto.setToDepartmentId(to.getId());
        assertThatThrownBy(() -> service.createTransferRequest(dto)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveAndCompleteRequireFreshMfa() throws Exception {
        assertThat(AssetTransferController.class.getMethod("approveTransfer", UUID.class)
                .isAnnotationPresent(RequireFreshMfa.class)).isTrue();
        assertThat(AssetTransferController.class.getMethod("completeTransfer", UUID.class)
                .isAnnotationPresent(RequireFreshMfa.class)).isTrue();
    }
}
