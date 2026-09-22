package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /purchase-orders/{id}/reject: why the order is refused. */
public record PurchaseOrderRejectRequest(
        @NotBlank(message = "A rejection reason is required")
        @Size(max = 5000)
        String reason) {
}
