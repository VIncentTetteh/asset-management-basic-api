package com.example.demo.controllers.v1;

import com.example.demo.dto.MaintenanceRecordDto;
import com.example.demo.services.MaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','SCHEDULE_MAINTENANCE','MARK_MAINTENANCE_COMPLETE')")
    public ResponseEntity<MaintenanceRecordDto> createMaintenanceRecord(
            @Valid @RequestBody MaintenanceRecordDto recordDto) {
        MaintenanceRecordDto createdRecord = maintenanceService.createMaintenanceRecord(recordDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRecord);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_MAINTENANCE')")
    public ResponseEntity<MaintenanceRecordDto> getMaintenanceRecordById(@PathVariable UUID id) {
        MaintenanceRecordDto record = maintenanceService.getMaintenanceRecordById(id);
        return ResponseEntity.ok(record);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_MAINTENANCE')")
    public ResponseEntity<Set<MaintenanceRecordDto>> getMaintenanceRecords(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) LocalDate dueBefore) {

        if (assetId != null) {
            return ResponseEntity.ok(maintenanceService.getMaintenanceRecordsByAsset(assetId));
        } else if (vendorId != null) {
            return ResponseEntity.ok(maintenanceService.getMaintenanceRecordsByVendor(vendorId));
        } else if (dueBefore != null) {
            return ResponseEntity.ok(maintenanceService.getMaintenanceRecordsDueBy(dueBefore));
        }
        // H8: No filter → return all for this tenant org
        return ResponseEntity.ok(maintenanceService.getAllMaintenanceRecords());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','SCHEDULE_MAINTENANCE','MARK_MAINTENANCE_COMPLETE')")
    public ResponseEntity<MaintenanceRecordDto> updateMaintenanceRecord(@PathVariable UUID id,
            @Valid @RequestBody MaintenanceRecordDto recordDto) {
        MaintenanceRecordDto updatedRecord = maintenanceService.updateMaintenanceRecord(id, recordDto);
        return ResponseEntity.ok(updatedRecord);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','SCHEDULE_MAINTENANCE','MARK_MAINTENANCE_COMPLETE')")
    public ResponseEntity<MaintenanceRecordDto> patchMaintenanceRecord(@PathVariable UUID id,
            @RequestBody MaintenanceRecordDto recordDto) {
        MaintenanceRecordDto updatedRecord = maintenanceService.patchMaintenanceRecord(id, recordDto);
        return ResponseEntity.ok(updatedRecord);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','SCHEDULE_MAINTENANCE','MARK_MAINTENANCE_COMPLETE')")
    public ResponseEntity<MaintenanceRecordDto> completeMaintenanceRecord(@PathVariable UUID id) {
        MaintenanceRecordDto completedRecord = maintenanceService.completeMaintenanceRecord(id);
        return ResponseEntity.ok(completedRecord);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','SCHEDULE_MAINTENANCE','MARK_MAINTENANCE_COMPLETE')")
    public ResponseEntity<Void> deleteMaintenanceRecord(@PathVariable UUID id) {
        maintenanceService.deleteMaintenanceRecord(id);
        return ResponseEntity.noContent().build();
    }
}
