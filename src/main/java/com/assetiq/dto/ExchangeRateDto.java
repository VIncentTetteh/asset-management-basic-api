package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeRateDto {
    private UUID id;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Base currency must be a 3-letter ISO 4217 code")
    @Size(max = 3)
    private String baseCurrency;
    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Target currency must be a 3-letter ISO 4217 code")
    @Size(max = 3)
    private String targetCurrency;
    @NotNull
    @DecimalMin(value = "0.00000001", message = "Rate must be positive")
    @Digits(integer = 10, fraction = 8)
    private BigDecimal rate;
    private LocalDate effectiveDate;
    @Size(max = 50)
    private String source;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID organisationId;
}
