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
}
