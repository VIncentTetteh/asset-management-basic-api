package com.assetiq.services.mobile;

import com.assetiq.enums.CheckoutStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An overdue checkout as the Home queue needs it; built by a JPQL constructor
 * expression. The holder is the employee when the asset went to one, otherwise
 * the user it was checked out to.
 */
public record CheckoutQueueRow(UUID id, UUID assetId, String assetName, String userFirstName,
                               String userLastName, String employeeFirstName, String employeeLastName,
                               CheckoutStatus status, LocalDate expectedReturnDate) {
}
