package com.example.demo.controllers.v1;

import com.example.demo.dto.OrganisationDto;
import com.example.demo.services.OrganisationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;

    public OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @PostMapping
    public ResponseEntity<OrganisationDto> create(@Valid @RequestBody OrganisationDto dto) {
        return ResponseEntity.ok(organisationService.create(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganisationDto> get(@PathVariable UUID id) {
        OrganisationDto dto = organisationService.get(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<OrganisationDto>> list() {
        return ResponseEntity.ok(organisationService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganisationDto> update(@PathVariable UUID id, @Valid @RequestBody OrganisationDto dto) {
        try {
            return ResponseEntity.ok(organisationService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        organisationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
