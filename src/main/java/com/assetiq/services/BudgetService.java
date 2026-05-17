package com.assetiq.services;

import com.assetiq.dto.BudgetAdjustmentRequest;
import com.assetiq.dto.BudgetDto;
import com.assetiq.dto.BudgetSummaryDto;
import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.PagedResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BudgetService {

    BudgetDto create(BudgetDto dto);

    BudgetDto getById(UUID id);

    List<BudgetDto> listAll();

    BudgetDto update(UUID id, BudgetDto dto);

    BudgetDto patch(UUID id, BudgetDto dto);

    /** Records spend against a budget (e.g. from a purchase order). */
    @Deprecated
    BudgetDto recordSpend(UUID budgetId, BigDecimal amount);

    /** Records a manual budget adjustment with an audit note. */
    BudgetDto recordAdjustment(UUID budgetId, BudgetAdjustmentRequest request);

    /** Returns a paged list of expenses linked to the given budget. */
    PagedResponseDto<ExpenseDto> getExpenses(UUID budgetId, int page, int size);

    /** Returns an aggregate summary of all budgets, grouped by department. */
    BudgetSummaryDto getSummary();

    void delete(UUID id);
}
