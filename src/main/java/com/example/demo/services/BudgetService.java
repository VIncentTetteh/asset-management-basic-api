package com.example.demo.services;

import com.example.demo.dto.BudgetDto;

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
    BudgetDto recordSpend(UUID budgetId, BigDecimal amount);

    void delete(UUID id);
}
