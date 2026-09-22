package com.assetiq.controllers.v1;

import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.ExpenseFilterRequest;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.services.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.assetiq.security.annotation.RequireFreshMfa;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /** Submit a new expense for approval. */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER','MANAGE_EXPENSES')")
    public ResponseEntity<ExpenseDto> submit(@Valid @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.submit(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER','MANAGE_EXPENSES')")
    public ResponseEntity<ExpenseDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(expenseService.getById(id));
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER','MANAGE_EXPENSES')")
    public ResponseEntity<PagedResponseDto<ExpenseDto>> list(@ModelAttribute ExpenseFilterRequest req) {
        return ResponseEntity.ok(expenseService.listPaged(req));
    }

    /** Returns only SUBMITTED expenses awaiting approval. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','VIEW_REPORTS')")
    public ResponseEntity<List<ExpenseDto>> listPending() {
        return ResponseEntity.ok(expenseService.listPending());
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER','MANAGE_EXPENSES')")
    public ResponseEntity<List<ExpenseDto>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(expenseService.listByUser(userId));
    }

    /** Approve a submitted expense. */
    @PostMapping("/{id}/approve")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','APPROVE_BUDGET')")
    public ResponseEntity<ExpenseDto> approve(@PathVariable UUID id) {
        // Illegal transitions surface as 409 with the reason via GlobalExceptionHandler.
        return ResponseEntity.ok(expenseService.approve(id));
    }

    /** Reject a submitted expense with an optional reason. */
    @PostMapping("/{id}/reject")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','APPROVE_BUDGET')")
    public ResponseEntity<ExpenseDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(expenseService.reject(id, reason));
    }

    /** Deleting releases a pending expense's commitment or reverses an approved one's spend. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
