package com.assetiq.controllers.v1;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.dto.EmployeeChecklistDto;
import com.assetiq.dto.EmployeeChecklistItemDto;
import com.assetiq.dto.EmployeeDto;
import com.assetiq.enums.EmployeeStatus;
import com.assetiq.services.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_EMPLOYEES','MANAGE_EMPLOYEES')")
    public ResponseEntity<Page<EmployeeDto>> search(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "lastName", "firstName"));
        return ResponseEntity.ok(employeeService.search(departmentId, status, q, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES')")
    public ResponseEntity<EmployeeDto> create(@Valid @RequestBody EmployeeDto dto) {
        return ResponseEntity.ok(employeeService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_EMPLOYEES','MANAGE_EMPLOYEES')")
    public ResponseEntity<EmployeeDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES')")
    public ResponseEntity<EmployeeDto> update(@PathVariable UUID id, @Valid @RequestBody EmployeeDto dto) {
        return ResponseEntity.ok(employeeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Full checkout history (active + returned) for the employee, newest first. */
    @GetMapping("/{id}/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_EMPLOYEES','MANAGE_EMPLOYEES','CHECKOUT_ASSET')")
    public ResponseEntity<List<CheckoutRecordDto>> assets(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getAssetHistory(id));
    }

    @GetMapping("/{id}/checklists")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_EMPLOYEES','MANAGE_EMPLOYEES')")
    public ResponseEntity<List<EmployeeChecklistDto>> checklists(@PathVariable UUID id) {
        return ResponseEntity.ok(employeeService.getChecklists(id));
    }

    /**
     * Starts onboarding with an optional list of checklist items; items with
     * itemType=ASSET_ISSUE must carry an assetId and will check the asset out
     * to the employee when completed.
     */
    @PostMapping("/{id}/onboard")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES')")
    public ResponseEntity<EmployeeChecklistDto> onboard(
            @PathVariable UUID id,
            @RequestBody(required = false) List<@Valid EmployeeChecklistItemDto> items) {
        return ResponseEntity.ok(employeeService.onboard(id, items));
    }

    /**
     * Starts offboarding: creates a checklist with one ASSET_RETURN item per
     * asset the employee currently holds (plus any extra items supplied).
     * The employee becomes TERMINATED when the checklist completes.
     */
    @PostMapping("/{id}/offboard")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES','OFFBOARD_EMPLOYEE')")
    public ResponseEntity<EmployeeChecklistDto> offboard(
            @PathVariable UUID id,
            @RequestBody(required = false) List<@Valid EmployeeChecklistItemDto> extraItems) {
        return ResponseEntity.ok(employeeService.offboard(id, extraItems));
    }

    /** Completes (default) or reopens a checklist item. */
    @PatchMapping("/checklists/items/{itemId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_EMPLOYEES','OFFBOARD_EMPLOYEE')")
    public ResponseEntity<EmployeeChecklistItemDto> completeItem(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "true") boolean completed) {
        return ResponseEntity.ok(employeeService.completeChecklistItem(itemId, completed));
    }
}
