package com.example.demo.dto;

import com.example.demo.enums.ContractStatus;
import com.example.demo.enums.ContractType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class ContractDto {

    private UUID id;

    @NotBlank
    private String title;

    private String contractNumber;

    @NotNull
    private ContractType contractType;

    private ContractStatus status;

    private UUID supplierId;
    private String supplierName;

    private UUID assetId;
    private String assetName;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private Integer alertDaysBefore;

    private BigDecimal value;
    private String currency;

    private boolean autoRenew;

    private String documentUrl;
    private String notes;

    /** Computed: days until endDate from today (negative if expired). */
    private Long daysUntilExpiry;

    // ---- getters / setters ----

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public ContractType getContractType() { return contractType; }
    public void setContractType(ContractType contractType) { this.contractType = contractType; }

    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getAlertDaysBefore() { return alertDaysBefore; }
    public void setAlertDaysBefore(Integer alertDaysBefore) { this.alertDaysBefore = alertDaysBefore; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getDaysUntilExpiry() { return daysUntilExpiry; }
    public void setDaysUntilExpiry(Long daysUntilExpiry) { this.daysUntilExpiry = daysUntilExpiry; }
}
