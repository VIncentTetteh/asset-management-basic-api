package com.assetiq.services.impl;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.dto.EmployeeChecklistDto;
import com.assetiq.dto.EmployeeChecklistItemDto;
import com.assetiq.dto.EmployeeDto;
import com.assetiq.enums.ChecklistItemType;
import com.assetiq.enums.ChecklistStatus;
import com.assetiq.enums.ChecklistType;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.EmployeeStatus;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.CheckoutService;
import com.assetiq.services.EmployeeService;
import com.assetiq.services.NotificationService;
import com.assetiq.services.TenantAwareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeServiceImpl extends TenantAwareService implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final EmployeeRepository employeeRepository;
    private final EmployeeChecklistRepository checklistRepository;
    private final EmployeeChecklistItemRepository checklistItemRepository;
    private final CheckoutRecordRepository checkoutRepository;
    private final AssetRepository assetRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final CheckoutService checkoutService;
    private final NotificationService notificationService;

    public EmployeeServiceImpl(EmployeeRepository employeeRepository,
                               EmployeeChecklistRepository checklistRepository,
                               EmployeeChecklistItemRepository checklistItemRepository,
                               CheckoutRecordRepository checkoutRepository,
                               AssetRepository assetRepository,
                               DepartmentRepository departmentRepository,
                               UserRepository userRepository,
                               OrganisationRepository organisationRepository,
                               CheckoutService checkoutService,
                               NotificationService notificationService) {
        super(organisationRepository);
        this.employeeRepository = employeeRepository;
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checkoutRepository = checkoutRepository;
        this.assetRepository = assetRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.checkoutService = checkoutService;
        this.notificationService = notificationService;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Override
    public EmployeeDto create(EmployeeDto dto) {
        Organisation org = requireTenantOrg();

        if (dto.getEmployeeNumber() != null && !dto.getEmployeeNumber().isBlank()) {
            employeeRepository.findByOrganisationAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                            org, dto.getEmployeeNumber())
                    .ifPresent(existing -> {
                        throw new IllegalStateException(
                                "Employee number '" + dto.getEmployeeNumber() + "' is already in use.");
                    });
        }

        Employee employee = new Employee();
        applyDto(employee, dto, org);
        employee.setOrganisation(org);
        if (dto.getStatus() != null) {
            employee.setStatus(dto.getStatus());
        }

        Employee saved = employeeRepository.save(employee);
        log.info("Employee {} created in org {}", saved.getId(), org.getId());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDto get(UUID id) {
        return toDto(requireEmployee(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeDto> search(UUID departmentId, EmployeeStatus status, String q, Pageable pageable) {
        Organisation org = requireTenantOrg();
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return employeeRepository.search(org, departmentId, status, query, pageable).map(this::toDto);
    }

    @Override
    public EmployeeDto update(UUID id, EmployeeDto dto) {
        Employee employee = requireEmployee(id);
        Organisation org = employee.getOrganisation();

        if (dto.getEmployeeNumber() != null && !dto.getEmployeeNumber().isBlank()) {
            employeeRepository.findByOrganisationAndEmployeeNumberIgnoreCaseAndDeletedAtIsNull(
                            org, dto.getEmployeeNumber())
                    .filter(other -> !other.getId().equals(employee.getId()))
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "Employee number '" + dto.getEmployeeNumber() + "' is already in use.");
                    });
        }

        if (dto.getStatus() == EmployeeStatus.TERMINATED && employee.getStatus() != EmployeeStatus.TERMINATED) {
            requireNoActiveCheckouts(employee,
                    "Cannot terminate: employee still holds assets. Run offboarding to return them first.");
            if (employee.getTerminationDate() == null && dto.getTerminationDate() == null) {
                employee.setTerminationDate(LocalDate.now());
            }
        }

        applyDto(employee, dto, org);
        if (dto.getStatus() != null) {
            employee.setStatus(dto.getStatus());
        }

        return toDto(employeeRepository.save(employee));
    }

    @Override
    public void delete(UUID id) {
        Employee employee = requireEmployee(id);
        requireNoActiveCheckouts(employee,
                "Cannot delete: employee still holds assets. Check them in or run offboarding first.");
        employee.setDeletedAt(Instant.now());
        employeeRepository.save(employee);
        log.info("Employee {} soft-deleted", id);
    }

    // ── Assets & checklists ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutRecordDto> getAssetHistory(UUID id) {
        requireEmployee(id); // tenant + existence check
        return checkoutService.listByEmployee(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeChecklistDto> getChecklists(UUID id) {
        Employee employee = requireEmployee(id);
        return checklistRepository.findByEmployeeAndDeletedAtIsNullOrderByCreatedAtDesc(employee)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public EmployeeChecklistDto onboard(UUID id, List<EmployeeChecklistItemDto> items) {
        Employee employee = requireEmployee(id);
        Organisation org = employee.getOrganisation();

        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Cannot onboard a terminated employee.");
        }
        requireNoOpenChecklist(employee, ChecklistType.ONBOARDING);

        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setEmployee(employee);
        checklist.setChecklistType(ChecklistType.ONBOARDING);
        checklist.setOrganisation(org);

        int order = 0;
        if (items != null) {
            for (EmployeeChecklistItemDto itemDto : items) {
                EmployeeChecklistItem item = new EmployeeChecklistItem();
                item.setChecklist(checklist);
                item.setTitle(itemDto.getTitle());
                item.setSortOrder(order++);
                if (itemDto.getItemType() == ChecklistItemType.ASSET_ISSUE) {
                    Asset asset = assetRepository
                            .findByIdAndOrganisationAndDeletedAtIsNull(itemDto.getAssetId(), org)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Asset not found: " + itemDto.getAssetId()));
                    item.setItemType(ChecklistItemType.ASSET_ISSUE);
                    item.setAsset(asset);
                } else {
                    item.setItemType(ChecklistItemType.GENERAL);
                }
                checklist.getItems().add(item);
            }
        }

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            employee.setStatus(EmployeeStatus.ONBOARDING);
        }

        EmployeeChecklist saved = checklistRepository.save(checklist);
        log.info("Onboarding checklist {} created for employee {} ({} items)",
                saved.getId(), id, saved.getItems().size());
        return toDto(saved);
    }

    @Override
    public EmployeeChecklistDto offboard(UUID id, List<EmployeeChecklistItemDto> extraItems) {
        Employee employee = requireEmployee(id);
        Organisation org = employee.getOrganisation();

        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new IllegalStateException("Employee is already terminated.");
        }
        requireNoOpenChecklist(employee, ChecklistType.OFFBOARDING);

        EmployeeChecklist checklist = new EmployeeChecklist();
        checklist.setEmployee(employee);
        checklist.setChecklistType(ChecklistType.OFFBOARDING);
        checklist.setOrganisation(org);

        int order = 0;
        List<CheckoutRecord> activeCheckouts =
                checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE);
        for (CheckoutRecord checkout : activeCheckouts) {
            EmployeeChecklistItem item = new EmployeeChecklistItem();
            item.setChecklist(checklist);
            item.setTitle("Return asset: " + checkout.getAsset().getName());
            item.setItemType(ChecklistItemType.ASSET_RETURN);
            item.setAsset(checkout.getAsset());
            item.setCheckoutRecord(checkout);
            item.setSortOrder(order++);
            checklist.getItems().add(item);
        }
        if (extraItems != null) {
            for (EmployeeChecklistItemDto itemDto : extraItems) {
                EmployeeChecklistItem item = new EmployeeChecklistItem();
                item.setChecklist(checklist);
                item.setTitle(itemDto.getTitle());
                item.setItemType(ChecklistItemType.GENERAL);
                item.setSortOrder(order++);
                checklist.getItems().add(item);
            }
        }

        employee.setStatus(EmployeeStatus.OFFBOARDING);

        // An offboarding with nothing to do completes immediately.
        EmployeeChecklist saved = checklistRepository.save(checklist);
        if (saved.getItems().isEmpty()) {
            completeChecklist(saved);
        }

        notificationService.notifyOrgAdmins(org, NotificationType.CHECKOUT,
                "Employee Offboarding Started",
                "Offboarding started for " + employee.getFullName() + " — "
                        + activeCheckouts.size() + " asset(s) to return.",
                saved.getId(), null);

        log.info("Offboarding checklist {} created for employee {} ({} asset returns)",
                saved.getId(), id, activeCheckouts.size());
        return toDto(saved);
    }

    @Override
    public EmployeeChecklistItemDto completeChecklistItem(UUID itemId, boolean completed) {
        Organisation org = requireTenantOrg();

        EmployeeChecklistItem item = checklistItemRepository.findById(itemId)
                .filter(i -> i.getDeletedAt() == null
                        && i.getChecklist().getOrganisation().getId().equals(org.getId())
                        && i.getChecklist().getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("Checklist item not found: " + itemId));

        EmployeeChecklist checklist = item.getChecklist();

        if (!completed) {
            if (item.getItemType() != ChecklistItemType.GENERAL) {
                throw new IllegalStateException(
                        "Asset issue/return items cannot be reopened — the checkout has already been processed.");
            }
            item.setCompleted(false);
            item.setCompletedBy(null);
            item.setCompletedAt(null);
            checklist.setStatus(ChecklistStatus.OPEN);
            checklist.setCompletedAt(null);
            return toDto(checklistItemRepository.save(item));
        }

        if (item.isCompleted()) {
            return toDto(item);
        }

        Employee employee = checklist.getEmployee();
        switch (item.getItemType()) {
            case ASSET_RETURN -> {
                CheckoutRecord checkout = item.getCheckoutRecord();
                if (checkout != null && checkout.getStatus() == CheckoutStatus.ACTIVE) {
                    checkoutService.checkIn(checkout.getId(), null);
                }
            }
            case ASSET_ISSUE -> {
                if (item.getAsset() != null) {
                    CheckoutRecordDto record =
                            checkoutService.checkOutToEmployee(item.getAsset().getId(), employee.getId(), null);
                    checkoutRepository.findById(record.getId()).ifPresent(item::setCheckoutRecord);
                }
            }
            case GENERAL -> { /* nothing to process */ }
        }

        item.setCompleted(true);
        item.setCompletedBy(resolveCurrentUser(org));
        item.setCompletedAt(Instant.now());
        checklistItemRepository.save(item);

        boolean allDone = checklist.getItems().stream()
                .filter(i -> i.getDeletedAt() == null)
                .allMatch(EmployeeChecklistItem::isCompleted);
        if (allDone) {
            completeChecklist(checklist);
        }

        return toDto(item);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void completeChecklist(EmployeeChecklist checklist) {
        checklist.setStatus(ChecklistStatus.COMPLETED);
        checklist.setCompletedAt(Instant.now());

        Employee employee = checklist.getEmployee();
        if (checklist.getChecklistType() == ChecklistType.OFFBOARDING) {
            employee.setStatus(EmployeeStatus.TERMINATED);
            if (employee.getTerminationDate() == null) {
                employee.setTerminationDate(LocalDate.now());
            }
        } else if (employee.getStatus() == EmployeeStatus.ONBOARDING) {
            employee.setStatus(EmployeeStatus.ACTIVE);
        }
        checklistRepository.save(checklist);
        employeeRepository.save(employee);
        log.info("Checklist {} completed; employee {} now {}",
                checklist.getId(), employee.getId(), employee.getStatus());
    }

    private Employee requireEmployee(UUID id) {
        Organisation org = requireTenantOrg();
        return employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + id));
    }

    private void requireNoActiveCheckouts(Employee employee, String message) {
        if (!checkoutRepository.findByEmployeeAndStatusAndDeletedAtIsNull(employee, CheckoutStatus.ACTIVE)
                .isEmpty()) {
            throw new IllegalStateException(message);
        }
    }

    private void requireNoOpenChecklist(Employee employee, ChecklistType type) {
        checklistRepository.findFirstByEmployeeAndChecklistTypeAndStatusAndDeletedAtIsNull(
                        employee, type, ChecklistStatus.OPEN)
                .ifPresent(open -> {
                    throw new IllegalStateException(
                            "An open " + type.name().toLowerCase() + " checklist already exists for this employee.");
                });
    }

    private void applyDto(Employee employee, EmployeeDto dto, Organisation org) {
        employee.setEmployeeNumber(dto.getEmployeeNumber());
        employee.setFirstName(dto.getFirstName());
        employee.setLastName(dto.getLastName());
        employee.setEmail(dto.getEmail());
        employee.setPhone(dto.getPhone());
        employee.setJobTitle(dto.getJobTitle());
        employee.setHireDate(dto.getHireDate());
        if (dto.getTerminationDate() != null) {
            employee.setTerminationDate(dto.getTerminationDate());
        }
        employee.setNotes(dto.getNotes());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .filter(d -> d.getOrganisation().getId().equals(org.getId()) && d.getDeletedAt() == null)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Department not found: " + dto.getDepartmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        if (dto.getManagerId() != null) {
            if (dto.getManagerId().equals(employee.getId())) {
                throw new IllegalArgumentException("An employee cannot be their own manager.");
            }
            Employee manager = employeeRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getManagerId(), org)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Manager employee not found: " + dto.getManagerId()));
            employee.setManager(manager);
        } else {
            employee.setManager(null);
        }

        if (dto.getUserId() != null) {
            User user = userRepository.findByIdAndOrganisationAndDeletedAtIsNull(dto.getUserId(), org)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + dto.getUserId()));
            employeeRepository.findByUserAndOrganisationAndDeletedAtIsNull(user, org)
                    .filter(other -> !other.getId().equals(employee.getId()))
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "That user account is already linked to employee " + other.getFullName() + ".");
                    });
            employee.setUser(user);
        } else {
            employee.setUser(null);
        }
    }

    private User resolveCurrentUser(Organisation org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmailAndOrganisationId(auth.getName(), org.getId()).orElse(null);
        }
        return null;
    }

    private EmployeeDto toDto(Employee e) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(e.getId());
        dto.setEmployeeNumber(e.getEmployeeNumber());
        dto.setFirstName(e.getFirstName());
        dto.setLastName(e.getLastName());
        dto.setEmail(e.getEmail());
        dto.setPhone(e.getPhone());
        dto.setJobTitle(e.getJobTitle());
        dto.setStatus(e.getStatus());
        dto.setHireDate(e.getHireDate());
        dto.setTerminationDate(e.getTerminationDate());
        dto.setNotes(e.getNotes());
        dto.setOrganisationId(e.getOrganisation().getId());
        if (e.getDepartment() != null) {
            dto.setDepartmentId(e.getDepartment().getId());
            dto.setDepartmentName(e.getDepartment().getName());
        }
        if (e.getManager() != null) {
            dto.setManagerId(e.getManager().getId());
            dto.setManagerName(e.getManager().getFullName());
        }
        if (e.getUser() != null) {
            dto.setUserId(e.getUser().getId());
        }
        dto.setActiveAssetCount((long) checkoutRepository
                .findByEmployeeAndStatusAndDeletedAtIsNull(e, CheckoutStatus.ACTIVE).size());
        return dto;
    }

    private EmployeeChecklistDto toDto(EmployeeChecklist c) {
        return EmployeeChecklistDto.builder()
                .id(c.getId())
                .employeeId(c.getEmployee().getId())
                .checklistType(c.getChecklistType())
                .status(c.getStatus())
                .completedAt(c.getCompletedAt())
                .createdAt(c.getCreatedAt())
                .items(c.getItems().stream()
                        .filter(i -> i.getDeletedAt() == null)
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private EmployeeChecklistItemDto toDto(EmployeeChecklistItem i) {
        EmployeeChecklistItemDto dto = EmployeeChecklistItemDto.builder()
                .id(i.getId())
                .checklistId(i.getChecklist().getId())
                .title(i.getTitle())
                .itemType(i.getItemType())
                .sortOrder(i.getSortOrder())
                .completed(i.isCompleted())
                .completedAt(i.getCompletedAt())
                .build();
        if (i.getAsset() != null) {
            dto.setAssetId(i.getAsset().getId());
            dto.setAssetName(i.getAsset().getName());
        }
        if (i.getCheckoutRecord() != null) {
            dto.setCheckoutRecordId(i.getCheckoutRecord().getId());
        }
        if (i.getCompletedBy() != null) {
            dto.setCompletedById(i.getCompletedBy().getId());
            dto.setCompletedByName(i.getCompletedBy().getFirstName() + " " + i.getCompletedBy().getLastName());
        }
        return dto;
    }
}
