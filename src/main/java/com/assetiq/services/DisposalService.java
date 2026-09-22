package com.assetiq.services;

import com.assetiq.dto.DisposalRecordDto;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface DisposalService {
    DisposalRecordDto createDisposalRecord(DisposalRecordDto recordDto);
    DisposalRecordDto getDisposalById(UUID id);
    Set<DisposalRecordDto> getDisposalsByAsset(UUID assetId);
    Set<DisposalRecordDto> getDisposalsByOrganisation(UUID organisationId);
    Set<DisposalRecordDto> getDisposalsByDateRange(LocalDate startDate, LocalDate endDate);
    Set<DisposalRecordDto> getDisposalsByApprover(UUID userId);
    DisposalRecordDto updateDisposalRecord(UUID id, DisposalRecordDto recordDto);
    DisposalRecordDto patchDisposalRecord(UUID id, DisposalRecordDto recordDto);
    void deleteDisposalRecord(UUID id);

    /** Checker step: a user other than the requester approves; the asset is disposed. */
    DisposalRecordDto approveDisposal(UUID id);

    /** Refuses (or, by the requester, withdraws) a pending disposal, recording why. */
    DisposalRecordDto rejectDisposal(UUID id, String reason);

    /**
     * The organisation's disposals matching every given filter (null = any), newest
     * disposal date first. Filters combine with AND.
     */
    java.util.List<DisposalRecordDto> searchDisposals(UUID assetId, LocalDate startDate, LocalDate endDate,
                                                      UUID approvedById, com.assetiq.enums.DisposalStatus status);
}
