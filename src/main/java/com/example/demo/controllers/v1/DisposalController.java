package com.example.demo.controllers.v1;

import com.example.demo.dto.DisposalRecordDto;
import com.example.demo.services.DisposalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<DisposalRecordDto> createDisposalRecord(@Valid @RequestBody DisposalRecordDto recordDto) {
        DisposalRecordDto createdRecord = disposalService.createDisposalRecord(recordDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRecord);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisposalRecordDto> getDisposalById(@PathVariable UUID id) {
        DisposalRecordDto record = disposalService.getDisposalById(id);
        return ResponseEntity.ok(record);
    }

    @GetMapping
    public ResponseEntity<Set<DisposalRecordDto>> getDisposals(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID organisationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) UUID approvedById) {

        if (assetId != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByAsset(assetId));
        } else if (organisationId != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByOrganisation(organisationId));
        } else if (startDate != null && endDate != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByDateRange(startDate, endDate));
        } else if (approvedById != null) {
            return ResponseEntity.ok(disposalService.getDisposalsByApprover(approvedById));
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<DisposalRecordDto> updateDisposalRecord(@PathVariable UUID id,
                                                                 @Valid @RequestBody DisposalRecordDto recordDto) {
        DisposalRecordDto updatedRecord = disposalService.updateDisposalRecord(id, recordDto);
        return ResponseEntity.ok(updatedRecord);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDisposalRecord(@PathVariable UUID id) {
        disposalService.deleteDisposalRecord(id);
        return ResponseEntity.noContent().build();
    }
}

