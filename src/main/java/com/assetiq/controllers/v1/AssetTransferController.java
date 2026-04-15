package com.assetiq.controllers.v1;

import com.assetiq.dto.AssetTransferDto;
import com.assetiq.services.AssetTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','TRANSFER_ASSET','VIEW_ASSETS')")
    public ResponseEntity<AssetTransferDto> createTransferRequest(
            @Valid @RequestBody AssetTransferDto transferDto) {
        AssetTransferDto createdTransfer = transferService.createTransferRequest(transferDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTransfer);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','TRANSFER_ASSET','VIEW_ASSETS')")
    public ResponseEntity<AssetTransferDto> getTransferById(@PathVariable UUID id) {
        AssetTransferDto transfer = transferService.getTransferById(id);
        return ResponseEntity.ok(transfer);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','TRANSFER_ASSET','VIEW_ASSETS')")
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
        // H8: No filter → return all for this tenant org
        return ResponseEntity.ok(transferService.getAllTransfers());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','TRANSFER_ASSET')")
    // C4 fix: approver identity derived from SecurityContext in service — no
    // approvedById param
    public ResponseEntity<AssetTransferDto> approveTransfer(@PathVariable UUID id) {
        AssetTransferDto approvedTransfer = transferService.approveTransfer(id);
        return ResponseEntity.ok(approvedTransfer);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','TRANSFER_ASSET')")
    public ResponseEntity<AssetTransferDto> rejectTransfer(@PathVariable UUID id) {
        AssetTransferDto rejectedTransfer = transferService.rejectTransfer(id);
        return ResponseEntity.ok(rejectedTransfer);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','TRANSFER_ASSET')")
    public ResponseEntity<AssetTransferDto> completeTransfer(@PathVariable UUID id) {
        AssetTransferDto completedTransfer = transferService.completeTransfer(id);
        return ResponseEntity.ok(completedTransfer);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','TRANSFER_ASSET')")
    public ResponseEntity<Void> deleteTransfer(@PathVariable UUID id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }
}
