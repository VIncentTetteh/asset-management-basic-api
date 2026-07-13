package com.assetiq.services.impl;

import com.assetiq.dto.EmployeeChecklistDto;
import com.assetiq.dto.EmployeeChecklistItemDto;
import com.assetiq.dto.EmployeeDto;
import com.assetiq.enums.ChecklistItemType;
import com.assetiq.enums.ChecklistStatus;
import com.assetiq.enums.ChecklistType;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.EmployeeStatus;
import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.CheckoutService;
import com.assetiq.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeChecklistRepository checklistRepository;
    @Mock private EmployeeChecklistItemRepository checklistItemRepository;
    @Mock private CheckoutRecordRepository checkoutRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private CheckoutService checkoutService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_defaultsToOnboardingStatus() {
        Organisation org = tenantOrganisation();
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(any(), any()))
                .thenReturn(List.of());

        EmployeeDto dto = new EmployeeDto();
        dto.setFirstName("Ama");
        dto.setLastName("Mensah");

        EmployeeDto created = employeeService.create(dto);

        assertEquals(EmployeeStatus.ONBOARDING, created.getStatus());
        assertEquals("Ama", created.getFirstName());
        assertEquals(org.getId(), created.getOrganisationId());
    }

    @Test
    void create_duplicateEmployeeNumber_throwsConflict() {
        Organisation org = tenantOrganisation();
        Employee existing = employeeWithOrg(org);
        when(employeeRepository.findByOrganisationAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(org, "EMP-1"))
                .thenReturn(Optional.of(existing));

        EmployeeDto dto = new EmployeeDto();
        dto.setFirstName("Ama");
        dto.setLastName("Mensah");
        dto.setEmployeeNumber("EMP-1");

        assertThrows(IllegalStateException.class, () -> employeeService.create(dto));
        verify(employeeRepository, never()).save(any());
    }

    // ── update / delete guards ────────────────────────────────────────────────

    @Test
    void update_terminateWithActiveCheckouts_throwsConflict() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE))
                .thenReturn(List.of(new CheckoutRecord()));

        EmployeeDto dto = new EmployeeDto();
        dto.setFirstName("Kofi");
        dto.setLastName("Boateng");
        dto.setStatus(EmployeeStatus.TERMINATED);

        assertThrows(IllegalStateException.class, () -> employeeService.update(employee.getId(), dto));
    }

    @Test
    void delete_withActiveCheckouts_throwsConflict() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE))
                .thenReturn(List.of(new CheckoutRecord()));

        assertThrows(IllegalStateException.class, () -> employeeService.delete(employee.getId()));
        assertNull(employee.getDeletedAt());
    }

    @Test
    void delete_withoutActiveCheckouts_softDeletes() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE))
                .thenReturn(List.of());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        employeeService.delete(employee.getId());

        assertNotNull(employee.getDeletedAt());
    }

    // ── offboarding ───────────────────────────────────────────────────────────

    @Test
    void offboard_createsReturnItemPerActiveCheckout_andSetsOffboarding() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checklistRepository.findFirstByEmployeeAndChecklistTypeAndStatusAndDeletedAtIsNull(
                employee, ChecklistType.OFFBOARDING, ChecklistStatus.OPEN))
                .thenReturn(Optional.empty());

        CheckoutRecord checkout1 = checkoutFor(employee, org, "Laptop");
        CheckoutRecord checkout2 = checkoutFor(employee, org, "Phone");
        when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE))
                .thenReturn(List.of(checkout1, checkout2));
        when(checklistRepository.save(any(EmployeeChecklist.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeChecklistDto checklist = employeeService.offboard(employee.getId(), null);

        assertEquals(ChecklistType.OFFBOARDING, checklist.getChecklistType());
        assertEquals(2, checklist.getItems().size());
        assertTrue(checklist.getItems().stream()
                .allMatch(i -> i.getItemType() == ChecklistItemType.ASSET_RETURN));
        assertEquals(EmployeeStatus.OFFBOARDING, employee.getStatus());
    }

    @Test
    void offboard_withNoAssets_terminatesImmediately() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));
        when(checklistRepository.findFirstByEmployeeAndChecklistTypeAndStatusAndDeletedAtIsNull(
                employee, ChecklistType.OFFBOARDING, ChecklistStatus.OPEN))
                .thenReturn(Optional.empty());
        when(checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE))
                .thenReturn(List.of());
        when(checklistRepository.save(any(EmployeeChecklist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        employeeService.offboard(employee.getId(), null);

        assertEquals(EmployeeStatus.TERMINATED, employee.getStatus());
        assertNotNull(employee.getTerminationDate());
    }

    @Test
    void offboard_alreadyTerminated_throwsConflict() {
        Organisation org = tenantOrganisation();
        Employee employee = employeeWithOrg(org);
        employee.setStatus(EmployeeStatus.TERMINATED);
        when(employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(employee.getId(), org))
                .thenReturn(Optional.of(employee));

        assertThrows(IllegalStateException.class, () -> employeeService.offboard(employee.getId(), null));
    }

    // ── checklist completion ──────────────────────────────────────────────────

    @Test
    void completeItem_assetReturn_checksInAndTerminatesWhenLast() {
        Organisation org = tenantOrganisation();
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        Employee employee = employeeWithOrg(org);
        employee.setStatus(EmployeeStatus.OFFBOARDING);

        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setId(UUID.randomUUID());
        checklist.setEmployee(employee);
        checklist.setChecklistType(ChecklistType.OFFBOARDING);
        checklist.setOrganisation(org);

        CheckoutRecord checkout = checkoutFor(employee, org, "Laptop");
        EmployeeChecklistItem item = new EmployeeChecklistItem();
        item.setId(UUID.randomUUID());
        item.setChecklist(checklist);
        item.setTitle("Return asset: Laptop");
        item.setItemType(ChecklistItemType.ASSET_RETURN);
        item.setCheckoutRecord(checkout);
        checklist.getItems().add(item);

        when(checklistItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(checklistItemRepository.save(any(EmployeeChecklistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepository.save(any(EmployeeChecklist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeChecklistItemDto result = employeeService.completeChecklistItem(item.getId(), true);

        assertTrue(result.isCompleted());
        verify(checkoutService).checkIn(eq(checkout.getId()), any());
        assertEquals(ChecklistStatus.COMPLETED, checklist.getStatus());
        assertEquals(EmployeeStatus.TERMINATED, employee.getStatus());
    }

    @Test
    void completeItem_generalItem_doesNotTouchCheckouts() {
        Organisation org = tenantOrganisation();
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        Employee employee = employeeWithOrg(org);
        employee.setStatus(EmployeeStatus.ONBOARDING);

        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setId(UUID.randomUUID());
        checklist.setEmployee(employee);
        checklist.setChecklistType(ChecklistType.ONBOARDING);
        checklist.setOrganisation(org);

        EmployeeChecklistItem item = new EmployeeChecklistItem();
        item.setId(UUID.randomUUID());
        item.setChecklist(checklist);
        item.setTitle("Sign NDA");
        checklist.getItems().add(item);

        when(checklistItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(checklistItemRepository.save(any(EmployeeChecklistItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(checklistRepository.save(any(EmployeeChecklist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        employeeService.completeChecklistItem(item.getId(), true);

        verify(checkoutService, never()).checkIn(any(), any());
        verify(checkoutService, never()).checkOutToEmployee(any(), any(), any());
        // Completing the only onboarding item activates the employee.
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
    }

    @Test
    void completeItem_reopeningAssetItem_throwsConflict() {
        Organisation org = tenantOrganisation();
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));

        Employee employee = employeeWithOrg(org);
        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setId(UUID.randomUUID());
        checklist.setEmployee(employee);
        checklist.setChecklistType(ChecklistType.OFFBOARDING);
        checklist.setOrganisation(org);

        EmployeeChecklistItem item = new EmployeeChecklistItem();
        item.setId(UUID.randomUUID());
        item.setChecklist(checklist);
        item.setTitle("Return asset: Laptop");
        item.setItemType(ChecklistItemType.ASSET_RETURN);
        item.setCompleted(true);

        when(checklistItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class,
                () -> employeeService.completeChecklistItem(item.getId(), false));
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

    private Employee employeeWithOrg(Organisation org) {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        employee.setFirstName("Kofi");
        employee.setLastName("Boateng");
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setOrganisation(org);
        return employee;
    }

    private CheckoutRecord checkoutFor(Employee employee, Organisation org, String assetName) {
        Asset asset = new Asset();
        asset.setId(UUID.randomUUID());
        asset.setName(assetName);
        CheckoutRecord record = new CheckoutRecord();
        record.setId(UUID.randomUUID());
        record.setAsset(asset);
        record.setEmployee(employee);
        record.setOrganisation(org);
        record.setStatus(CheckoutStatus.ACTIVE);
        return record;
    }
}
