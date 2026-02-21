package com.example.demo.controllers.v1;

import com.example.demo.dto.AssetTransferDto;
import com.example.demo.services.AssetTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asset-transfers")
public class AssetTransferController {

    private final AssetTransferService transferService;

    public AssetTransferController(AssetTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<AssetTransferDto> createTransferRequest(@Valid @RequestBody AssetTransferDto transferDto) {
        AssetTransferDto createdTransfer = transferService.createTransferRequest(transferDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTransfer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetTransferDto> getTransferById(@PathVariable UUID id) {
        AssetTransferDto transfer = transferService.getTransferById(id);
        return ResponseEntity.ok(transfer);
    }

    @GetMapping
    public ResponseEntity<Set<AssetTransferDto>> getTransfers(
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID fromDepartmentId,
            @RequestParam(required = false) UUID toDepartmentId,
            @RequestParam(required = false) UUID requestedById) {

        if (assetId != null) {
            return ResponseEntity.ok(transferService.getTransfersByAsset(assetId));
        } else if (fromDepartmentId != null) {
            return ResponseEntity.ok(transferService.getTransfersFromDepartment(fromDepartmentId));
        } else if (toDepartmentId != null) {
            return ResponseEntity.ok(transferService.getTransfersToDepartment(toDepartmentId));
        } else if (requestedById != null) {
            return ResponseEntity.ok(transferService.getTransfersByRequester(requestedById));
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AssetTransferDto> approveTransfer(@PathVariable UUID id,
                                                           @RequestParam UUID approvedById) {
        AssetTransferDto approvedTransfer = transferService.approveTransfer(id, approvedById);
        return ResponseEntity.ok(approvedTransfer);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AssetTransferDto> rejectTransfer(@PathVariable UUID id) {
        AssetTransferDto rejectedTransfer = transferService.rejectTransfer(id);
        return ResponseEntity.ok(rejectedTransfer);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<AssetTransferDto> completeTransfer(@PathVariable UUID id) {
        AssetTransferDto completedTransfer = transferService.completeTransfer(id);
        return ResponseEntity.ok(completedTransfer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransfer(@PathVariable UUID id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }
}

