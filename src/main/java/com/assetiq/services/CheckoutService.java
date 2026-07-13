package com.assetiq.services;

import com.assetiq.dto.CheckoutRecordDto;

import java.util.List;
import java.util.UUID;

public interface CheckoutService {

    /** Check an asset out to a user. Creates a CheckoutRecord and transitions the asset to IN_USE. */
    CheckoutRecordDto checkOut(UUID assetId, UUID userId, CheckoutRecordDto dto);

    /**
     * Check an asset out to an employee (who may not be a system user). The
     * acting authenticated user is recorded as {@code checkedOutBy}; the
     * employee is the recipient.
     */
    CheckoutRecordDto checkOutToEmployee(UUID assetId, UUID employeeId, CheckoutRecordDto dto);

    /** Check an asset back in. Marks the record RETURNED and transitions the asset to IN_STOCK. */
    CheckoutRecordDto checkIn(UUID checkoutRecordId, CheckoutRecordDto dto);

    CheckoutRecordDto getById(UUID id);

    List<CheckoutRecordDto> listByOrg();

    List<CheckoutRecordDto> listByAsset(UUID assetId);

    List<CheckoutRecordDto> listByUser(UUID userId);

    /** All checkout records for an employee, newest first. */
    List<CheckoutRecordDto> listByEmployee(UUID employeeId);

    /** Returns all ACTIVE checkout records where expectedReturnDate has passed. */
    List<CheckoutRecordDto> listOverdue();
}
