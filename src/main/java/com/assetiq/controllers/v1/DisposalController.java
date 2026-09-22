package com.assetiq.controllers.v1;

import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.services.DisposalService;
import com.assetiq.validation.OnCreate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disposals")
public class DisposalController {

    private final DisposalService disposalService;

    public DisposalController(DisposalService disposalService) {
        this.disposalService = disposalService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    public ResponseEntity<DisposalRecordDto> createDisposalRecord(
            @Validated(OnCreate.class) @RequestBody DisposalRecordDto recordDto) {
        DisposalRecordDto createdRecord = disposalService.createDisposalRecord(recordDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRecord);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','DISPOSE_ASSET')")
    public ResponseEntity<DisposalRecordDto> getDisposalById(@PathVariable UUID id) {
        DisposalRecordDto record = disposalService.getDisposalById(id);
        return ResponseEntity.ok(record);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','DISPOSE_ASSET')")
    public ResponseEntity<Set<DisposalRecordDto>> getDisposals(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID approvedById) {

        if (assetId != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByAsset(assetId));
        } else if (startDate != null && endDate != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByDateRange(startDate, endDate));
        } else if (approvedById != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByApprover(approvedById));
        }
        // H8: No filter → return all for this tenant org
        return ResponseEntity.ok(disposalService.getDisposalsByOrganisation(null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    public ResponseEntity<DisposalRecordDto> updateDisposalRecord(@PathVariable UUID id,
            @Validated(OnCreate.class) @RequestBody DisposalRecordDto recordDto) {
        DisposalRecordDto updatedRecord = disposalService.updateDisposalRecord(id, recordDto);
        return ResponseEntity.ok(updatedRecord);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    public ResponseEntity<DisposalRecordDto> patchDisposalRecord(@PathVariable UUID id,
            @Valid @RequestBody DisposalRecordDto recordDto) {
        DisposalRecordDto updatedRecord = disposalService.patchDisposalRecord(id, recordDto);
        return ResponseEntity.ok(updatedRecord);
    }

    /** Checker step: a different user approves; the asset becomes DISPOSED. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    @RequireFreshMfa
    public ResponseEntity<DisposalRecordDto> approveDisposal(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.approveDisposal(id));
    }

    /** Refuses a pending disposal (the requester may use it to withdraw). */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    public ResponseEntity<DisposalRecordDto> rejectDisposal(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.rejectDisposal(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','DISPOSE_ASSET')")
    public ResponseEntity<Void> deleteDisposalRecord(@PathVariable UUID id) {
        disposalService.deleteDisposalRecord(id);
        return ResponseEntity.noContent().build();
    }
}
