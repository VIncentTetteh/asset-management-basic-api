package com.assetiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BudgetSummaryDto {
    private BigDecimal totalAllocated;
    private BigDecimal totalSpent;
    private BigDecimal totalCommitted;
    private BigDecimal totalAvailable;
    private List<DepartmentSummary> byDepartment;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DepartmentSummary {
        private String     departmentId;    // null = org-wide budgets
        private String     departmentName;
        private BigDecimal allocated;
        private BigDecimal spent;
        private BigDecimal committed;
        private BigDecimal available;
    }
}
