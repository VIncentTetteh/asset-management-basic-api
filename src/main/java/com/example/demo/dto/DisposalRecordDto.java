package com.example.demo.dto;

import com.example.demo.enums.DisposalMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DisposalRecordDto {
    private UUID id;

    @NotNull(message = "Asset ID is required")
    private UUID assetId;

    @NotNull(message = "Disposal method is required")
    private DisposalMethod disposalMethod;

    @NotNull(message = "Disposal date is required")
    private LocalDate disposalDate;

    private BigDecimal saleValue;

    @NotNull(message = "Approved by user ID is required")
    private UUID approvedById;

    private String reason;

    private String complianceDocumentUrl;

    @NotNull(message = "Organisation ID is required")
    private UUID organisationId;
}

