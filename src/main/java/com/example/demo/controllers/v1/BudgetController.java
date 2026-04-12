package com.example.demo.controllers.v1;

import com.example.demo.dto.BudgetDto;
import com.example.demo.services.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<BudgetDto> create(@Valid @RequestBody BudgetDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_BUDGETS')")
    public ResponseEntity<BudgetDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(budgetService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_BUDGETS')")
    public ResponseEntity<List<BudgetDto>> list() {
        return ResponseEntity.ok(budgetService.listAll());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<BudgetDto> update(
            @PathVariable UUID id, @Valid @RequestBody BudgetDto dto) {
        return ResponseEntity.ok(budgetService.update(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<BudgetDto> patch(
            @PathVariable UUID id, @RequestBody BudgetDto dto) {
        return ResponseEntity.ok(budgetService.patch(id, dto));
    }

    /**
     * Records spend against a budget.
     * POST /api/v1/budgets/{id}/spend
     * Body: { "amount": 5000.00 }
     */
    @PostMapping("/{id}/spend")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<BudgetDto> recordSpend(
            @PathVariable UUID id,
            @RequestBody Map<String, BigDecimal> body) {
        BigDecimal amount = body.get("amount");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(budgetService.recordSpend(id, amount));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
