package com.assetiq.dto;

import com.assetiq.enums.CheckoutStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
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
    /** Who checked the asset back in (read-only). */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String checkedInByName;
    /** Stored in a VARCHAR(50) column. */
    @Size(max = 50)
    private String conditionOnCheckout;
    @Size(max = 50)
    private String conditionOnReturn;
    @Size(max = 2000)
    private String notes;
    private CheckoutStatus status;
    private UUID organisationId;
    private UUID employeeId;
    private String employeeName;
}
