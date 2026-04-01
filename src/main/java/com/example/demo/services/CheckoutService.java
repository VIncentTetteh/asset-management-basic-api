package com.example.demo.services;

import com.example.demo.dto.CheckoutRecordDto;

import java.util.List;
import java.util.UUID;

public interface CheckoutService {

    /** Check an asset out to a user. Creates a CheckoutRecord and transitions the asset to IN_USE. */
    CheckoutRecordDto checkOut(UUID assetId, UUID userId, CheckoutRecordDto dto);

    /** Check an asset back in. Marks the record RETURNED and transitions the asset to IN_STOCK. */
    CheckoutRecordDto checkIn(UUID checkoutRecordId, CheckoutRecordDto dto);

    CheckoutRecordDto getById(UUID id);

    List<CheckoutRecordDto> listByOrg();

    List<CheckoutRecordDto> listByAsset(UUID assetId);

    List<CheckoutRecordDto> listByUser(UUID userId);

    /** Returns all ACTIVE checkout records where expectedReturnDate has passed. */
    List<CheckoutRecordDto> listOverdue();
}
