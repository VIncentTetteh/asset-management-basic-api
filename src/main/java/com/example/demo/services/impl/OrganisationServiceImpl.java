package com.example.demo.services.impl;

import com.example.demo.dto.OrganisationDto;
import com.example.demo.models.Organisation;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.services.OrganisationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganisationServiceImpl implements OrganisationService {

    private final OrganisationRepository organisationRepository;

    public OrganisationServiceImpl(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    @Override
    public OrganisationDto create(OrganisationDto dto) {

        if (dto.name == null || dto.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Organisation name is required");
        }

        String name = dto.name.trim();

        if (organisationRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name)) {
            throw new IllegalStateException("Organisation with the same name already exists");
        }

        Organisation organisation = new Organisation();
        organisation.setName(name); // always use trimmed value

        Organisation saved = organisationRepository.save(organisation);
        return toDto(saved);
    }


    @Override
    public OrganisationDto get(UUID id) {
        return organisationRepository.findByIdAndDeletedAtIsNull(id).map(this::toDto).orElse(null);
    }

    @Override
    public List<OrganisationDto> list() {
        return organisationRepository.findAllByDeletedAtIsNull().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public OrganisationDto update(UUID id, OrganisationDto dto) {
        Organisation o = organisationRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        if (dto.name != null) o.setName(dto.name);
        Organisation saved = organisationRepository.save(o);
        return toDto(saved);
    }

    @Override
    public void delete(UUID id) {
        Organisation o = organisationRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        o.setDeletedAt(Instant.now());
        organisationRepository.save(o);
    }

    private OrganisationDto toDto(Organisation o) {
        OrganisationDto dto = new OrganisationDto();
        dto.id = o.getId();
        dto.name = o.getName();
        return dto;
    }
}
