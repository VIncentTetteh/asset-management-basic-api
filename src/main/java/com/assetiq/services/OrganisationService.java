package com.assetiq.services;

import com.assetiq.dto.OrganisationDto;

import java.util.List;
import java.util.UUID;

public interface OrganisationService {
    OrganisationDto create(OrganisationDto dto);
    OrganisationDto get(UUID id);
    List<OrganisationDto> list();
    OrganisationDto update(UUID id, OrganisationDto dto);
    OrganisationDto patch(UUID id, OrganisationDto dto);
    void delete(UUID id);
}
