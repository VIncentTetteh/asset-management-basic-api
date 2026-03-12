package com.example.demo.services;

import com.example.demo.dto.SoftwareLicenseDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SoftwareLicenseService {

    SoftwareLicenseDto create(SoftwareLicenseDto dto);

    SoftwareLicenseDto getById(UUID id);

    List<SoftwareLicenseDto> listAll();

    List<SoftwareLicenseDto> listExpiringSoon(int days);

    List<SoftwareLicenseDto> listOverAllocated();

    SoftwareLicenseDto update(UUID id, SoftwareLicenseDto dto);

    SoftwareLicenseDto patch(UUID id, SoftwareLicenseDto dto);

    void delete(UUID id);

    /** Returns utilization summary: totalSeats, usedSeats, availableSeats, utilizationPct per license. */
    Map<String, Object> getUtilizationSummary();
}
