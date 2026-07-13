package com.assetiq.services.impl;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.AssetStateTransitionService;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the employee-recipient checkout path added with the Employee module. */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplEmployeeTest {

    @Mock private CheckoutRecordRepository checkoutRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private NotificationService notificationService;
    @Mock private AssetStateTransitionService stateTransitionService;

    @InjectMocks
    private CheckoutServiceImpl checkoutService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkOutToEmployee_createsActiveRecordWithEmployeeRecipient() {
        Organisation org = tenantOrganisation();
        Asset asset = assetInOrg(org);
        Employee employee = employeeInOrg(org);
        User actor = actingUser(org);

        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org))
                .thenReturn(Optional.of(asset));
        when(checkoutRepository.findByAssetAndStatusAndDeletedAtIsNull(asset, CheckoutStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(userRepository.findByEmailAndOrganisationId(actor.getEmail(), org.getId()))
                .thenReturn(Optional.of(actor));
        when(checkoutRepository.save(any(CheckoutRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRecordDto dto = checkoutService.checkOutToEmployee(asset.getId(), employee.getId(), null);

        assertEquals(CheckoutStatus.ACTIVE, dto.getStatus());
        assertEquals(employee.getId(), dto.getEmployeeId());
        assertEquals(actor.getId(), dto.getCheckedOutById());
        verify(stateTransitionService).transition(any(Asset.class), any(AssetStatus.class), any(User.class), any());
    }

    @Test
    void checkOutToEmployee_alreadyCheckedOut_throwsConflict() {
        Organisation org = tenantOrganisation();
        Asset asset = assetInOrg(org);
        Employee employee = employeeInOrg(org);

        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org))
                .thenReturn(Optional.of(asset));
        when(checkoutRepository.findByAssetAndStatusAndDeletedAtIsNull(asset, CheckoutStatus.ACTIVE))
                .thenReturn(Optional.of(new CheckoutRecord()));

        assertThrows(IllegalStateException.class,
                () -> checkoutService.checkOutToEmployee(asset.getId(), employee.getId(), null));
        verify(checkoutRepository, never()).save(any());
    }

    @Test
    void checkOutToEmployee_unknownEmployee_throwsBadRequest() {
        Organisation org = tenantOrganisation();
        Asset asset = assetInOrg(org);
        UUID employeeId = UUID.randomUUID();

        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(asset.getId(), org))
                .thenReturn(Optional.of(asset));
        when(checkoutRepository.findByAssetAndStatusAndDeletedAtIsNull(asset, CheckoutStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employeeId, org))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkOutToEmployee(asset.getId(), employeeId, null));
    }

    @Test
    void listByEmployee_returnsMappedHistory() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeInOrg(org);
        Asset asset = assetInOrg(org);
        User actor = actingUser(org);

        CheckoutRecord record = new CheckoutRecord();
        record.setId(UUID.randomUUID());
        record.setAsset(asset);
        record.setCheckedOutBy(actor);
        record.setEmployee(employee);
        record.setOrganisation(org);
        record.setStatus(CheckoutStatus.RETURNED);

        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checkoutRepository.findByEmployeeAndDeletedAtIsNullOrderByCheckedOutAtDesc(employee))
                .thenReturn(List.of(record));

        List<CheckoutRecordDto> history = checkoutService.listByEmployee(employee.getId());

        assertEquals(1, history.size());
        assertEquals(employee.getId(), history.get(0).getEmployeeId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Organisation tenantOrganisation() {
        Organisation organisation = new Organisation();
        organisation.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(organisation.getId());
        lenient().when(organisationRepository.findByIdAndDeletedAtIsNull(organisation.getId()))
                .thenReturn(Optional.of(organisation));
        return organisation;
    }

    private Asset assetInOrg(Organisation org) {
        Asset asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName("MacBook Pro");
        asset.setStatus(AssetStatus.IN_STOCK);
        asset.setOrganisation(org);
        return asset;
    }

    private Employee employeeInOrg(Organisation org) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFirstName("Ama");
        employee.setLastName("Mensah");
        employee.setOrganisation(org);
        return employee;
    }

    private User actingUser(Organisation org) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("admin@example.com");
        user.setFirstName("Org");
        user.setLastName("Admin");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "n/a", List.of()));
        return user;
    }
}
