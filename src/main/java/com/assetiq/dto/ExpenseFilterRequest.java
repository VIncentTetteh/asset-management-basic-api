package com.assetiq.dto;

import com.assetiq.enums.ExpenseCategory;
import com.assetiq.enums.ExpenseStatus;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseFilterRequest(
    String          search,
    ExpenseStatus   status,
    ExpenseCategory category,
    UUID            linkedBudgetId,
    UUID            linkedAssetId,
    UUID            submittedUserId,
    UUID            departmentId,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
    Integer         page,
    Integer         size
) {}
