package com.assetiq.dto;

import com.assetiq.enums.BudgetStatus;
import com.assetiq.validation.NullOrNotBlank;
import com.assetiq.validation.OnCreate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class BudgetDto {

    private UUID id;

    @NotBlank(groups = OnCreate.class)
    @NullOrNotBlank
    @Size(max = 255)
    private String name;

    private String description;

    private UUID departmentId;
    private String departmentName;

    @NotNull(groups = OnCreate.class)
    @DecimalMin("0.01")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal totalAmount;

    /** Read-only: maintained by the budget ledger, ignored on create/update. */
    @Digits(integer = 13, fraction = 2)
    private BigDecimal spentAmount;

    /** Read-only: open purchase-order and expense commitments against this budget. */
    @Digits(integer = 13, fraction = 2)
    private BigDecimal committedAmount;

    /**
     * Computed: totalAmount - spentAmount - committedAmount.
     * True available headroom accounting for in-flight approvals.
     */
    private BigDecimal availableAmount;

    /**
     * Linear projection: spentAmount / elapsedDays * totalPeriodDays.
     * Null when period hasn't started or totalAmount is zero.
     */
    private BigDecimal forecastedSpend;

    /** Configurable alert threshold (default 80). */
    @Min(1)
    @Max(100)
    private Integer alertThresholdPct;

    @Size(max = 3)
    private String currency;

    @NotNull(groups = OnCreate.class)
    private LocalDate periodStart;

    @NotNull(groups = OnCreate.class)
    private LocalDate periodEnd;

    private BudgetStatus status;

    /** Computed: totalAmount - spentAmount */
    private BigDecimal remainingAmount;

    /** Computed: spentAmount / totalAmount * 100 */
    private Double utilizationPct;

    private Integer fiscalYear;

    private Instant createdAt;

    private Instant updatedAt;

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getSpentAmount() { return spentAmount; }
    public void setSpentAmount(BigDecimal spentAmount) { this.spentAmount = spentAmount; }

    public BigDecimal getCommittedAmount() { return committedAmount; }
    public void setCommittedAmount(BigDecimal committedAmount) { this.committedAmount = committedAmount; }

    public BigDecimal getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(BigDecimal availableAmount) { this.availableAmount = availableAmount; }

    public BigDecimal getForecastedSpend() { return forecastedSpend; }
    public void setForecastedSpend(BigDecimal forecastedSpend) { this.forecastedSpend = forecastedSpend; }

    public Integer getAlertThresholdPct() { return alertThresholdPct; }
    public void setAlertThresholdPct(Integer alertThresholdPct) { this.alertThresholdPct = alertThresholdPct; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BudgetStatus getStatus() { return status; }
    public void setStatus(BudgetStatus status) { this.status = status; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public Double getUtilizationPct() { return utilizationPct; }
    public void setUtilizationPct(Double utilizationPct) { this.utilizationPct = utilizationPct; }

    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
