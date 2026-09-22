package com.assetiq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a purchase order, nested in {@link PurchaseOrderDto}.
 *
 * <p>Every constraint mirrors the po_line_item column it is stored in (V56), so a
 * request that would not fit the column is a field error rather than a database
 * error. {@code lineNumber}, {@code lineTotal} and the derived {@code taxAmount}
 * are server-owned: a client cannot post a line total that disagrees with its own
 * quantity and price.
 */
@Data
public class PoLineItemDto {

    /** Present on read; ignored on write (the set is replaced wholesale). */
    private UUID id;

    /** Read-only: the server numbers the lines 1..n in the order they arrive. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer lineNumber;

    @NotBlank(message = "Description is required")
    @Size(max = 500)
    private String description;

    @Size(max = 100)
    private String supplierPartNumber;

    /** Optional asset category to book this line against. */
    private UUID categoryId;

    /** Read-only display name of {@link #categoryId}. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String categoryName;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be greater than zero")
    @Digits(integer = 11, fraction = 4)
    private BigDecimal quantity;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0", message = "Unit price cannot be negative")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal unitPrice;

    /** Optional tax percentage (12.5 = 12.5%). When given, the tax amount is derived from it. */
    @DecimalMin(value = "0", message = "Tax rate cannot be negative")
    @Digits(integer = 5, fraction = 4)
    private BigDecimal taxRate;

    /**
     * Tax in money. Accepted only when no {@link #taxRate} is supplied; when a rate
     * is given this is recomputed from it and whatever the client sent is ignored.
     */
    @DecimalMin(value = "0", message = "Tax amount cannot be negative")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal taxAmount;

    /** Read-only: quantity x unit price + tax. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal lineTotal;
}
