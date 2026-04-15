package com.assetiq.services;

import com.assetiq.dto.LeaseRecordDto;

import java.util.List;
import java.util.UUID;

public interface LeaseRecordService {

    /** Create a new lease record for an asset. */
    LeaseRecordDto create(LeaseRecordDto dto);

    /** Update an existing lease record. */
    LeaseRecordDto update(UUID id, LeaseRecordDto dto);

    LeaseRecordDto getById(UUID id);

    List<LeaseRecordDto> listAll();

    List<LeaseRecordDto> listByAsset(UUID assetId);

    /**
     * Returns ACTIVE leases whose end date is within the next {@code daysAhead} days.
     */
    List<LeaseRecordDto> listExpiringSoon(int daysAhead);

    /** Terminate a lease early. */
    LeaseRecordDto terminate(UUID id, String reason);

    void delete(UUID id);
}
