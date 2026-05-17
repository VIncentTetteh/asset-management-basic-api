package com.assetiq.controllers.v1;

import com.assetiq.dto.BudgetAdjustmentRequest;
import com.assetiq.dto.BudgetDto;
import com.assetiq.dto.BudgetSummaryDto;
import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.services.BudgetService;
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

    // /summary must come before /{id} so Spring MVC does not treat "summary" as a UUID path variable
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','VIEW_BUDGETS')")
    public ResponseEntity<BudgetSummaryDto> getSummary() {
        return ResponseEntity.ok(budgetService.getSummary());
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
     *
     * @deprecated Use POST /{id}/adjustment instead, which records the note and tracks committed amount.
     */
    @Deprecated
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

    @GetMapping("/{id}/expenses")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_BUDGETS')")
    public ResponseEntity<PagedResponseDto<ExpenseDto>> getBudgetExpenses(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(budgetService.getExpenses(id, page, size));
    }

    @PostMapping("/{id}/adjustment")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<BudgetDto> recordAdjustment(@PathVariable UUID id,
            @Valid @RequestBody BudgetAdjustmentRequest request) {
        return ResponseEntity.ok(budgetService.recordAdjustment(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_BUDGETS','APPROVE_BUDGET')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
