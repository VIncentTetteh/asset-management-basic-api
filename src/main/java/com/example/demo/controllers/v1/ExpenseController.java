package com.example.demo.controllers.v1;

import com.example.demo.dto.ExpenseDto;
import com.example.demo.services.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<ExpenseDto> submit(@Valid @RequestBody ExpenseDto dto) {
        return ResponseEntity.ok(expenseService.submit(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<ExpenseDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(expenseService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<ExpenseDto>> listAll() {
        return ResponseEntity.ok(expenseService.listAll());
    }

    /** Returns only SUBMITTED expenses awaiting approval. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','VIEW_REPORTS')")
    public ResponseEntity<List<ExpenseDto>> listPending() {
        return ResponseEntity.ok(expenseService.listPending());
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<List<ExpenseDto>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(expenseService.listByUser(userId));
    }

    /** Approve a submitted expense. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','VIEW_REPORTS')")
    public ResponseEntity<ExpenseDto> approve(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(expenseService.approve(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Reject a submitted expense with an optional reason. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','VIEW_REPORTS')")
    public ResponseEntity<ExpenseDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        try {
            return ResponseEntity.ok(expenseService.reject(id, reason));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXPENSES','VIEW_REPORTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
