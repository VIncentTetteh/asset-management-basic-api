package com.assetiq.dto;

import com.assetiq.enums.CheckoutStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckoutRecordDto {
    private UUID id;
    private UUID assetId;
    private String assetName;
    private UUID checkedOutById;
    private String checkedOutByName;
    private Instant checkedOutAt;
    private LocalDate expectedReturnDate;
    private LocalDate actualReturnDate;
    private UUID checkedInById;
    private String conditionOnCheckout;
    private String conditionOnReturn;
    private String notes;
    private CheckoutStatus status;
    private UUID organisationId;
}
