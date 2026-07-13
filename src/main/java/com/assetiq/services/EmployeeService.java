package com.assetiq.services;

import com.assetiq.dto.CheckoutRecordDto;
import com.assetiq.dto.EmployeeChecklistDto;
import com.assetiq.dto.EmployeeChecklistItemDto;
import com.assetiq.dto.EmployeeDto;
import com.assetiq.enums.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {

    EmployeeDto create(EmployeeDto dto);

    EmployeeDto get(UUID id);

    Page<EmployeeDto> search(UUID departmentId, EmployeeStatus status, String q, Pageable pageable);

    EmployeeDto update(UUID id, EmployeeDto dto);

    void delete(UUID id);

    /** All checkout records (active + returned) for this employee, newest first. */
    List<CheckoutRecordDto> getAssetHistory(UUID id);

    List<EmployeeChecklistDto> getChecklists(UUID id);

    /**
     * Starts onboarding: creates an ONBOARDING checklist from the given items
     * (ASSET_ISSUE items reference the assets to hand over) and sets the
     * employee's status to ONBOARDING if not already active.
     */
    EmployeeChecklistDto onboard(UUID id, List<EmployeeChecklistItemDto> items);

    /**
     * Starts offboarding: sets status to OFFBOARDING and creates an OFFBOARDING
     * checklist with one ASSET_RETURN item per ACTIVE checkout plus any extra
     * items supplied.
     */
    EmployeeChecklistDto offboard(UUID id, List<EmployeeChecklistItemDto> extraItems);

    /**
     * Completes (or reopens) a checklist item. Completing an ASSET_RETURN item
     * checks the linked checkout record in; completing an ASSET_ISSUE item
     * checks the linked asset out to the employee. When the last open item of
     * an OFFBOARDING checklist completes, the employee becomes TERMINATED; for
     * ONBOARDING, ACTIVE.
     */
    EmployeeChecklistItemDto completeChecklistItem(UUID itemId, boolean completed);
}
