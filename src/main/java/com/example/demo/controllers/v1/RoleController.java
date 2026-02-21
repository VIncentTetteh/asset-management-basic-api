package com.example.demo.controllers.v1;

import com.example.demo.dto.RoleDto;
import com.example.demo.services.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody RoleDto roleDto,
                                             @RequestParam UUID organisationId) {
        RoleDto createdRole = roleService.createRole(roleDto, organisationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRole);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> getRoleById(@PathVariable UUID id) {
        RoleDto role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }

    @GetMapping
    public ResponseEntity<Set<RoleDto>> getRolesByOrganisation(@RequestParam UUID organisationId) {
        Set<RoleDto> roles = roleService.getRolesByOrganisation(organisationId);
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto> updateRole(@PathVariable UUID id,
                                             @Valid @RequestBody RoleDto roleDto) {
        RoleDto updatedRole = roleService.updateRole(id, roleDto);
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-name")
    public ResponseEntity<RoleDto> getRoleByName(@RequestParam String name,
                                                @RequestParam UUID organisationId) {
        RoleDto role = roleService.getRoleByNameAndOrganisation(name, organisationId);
        return ResponseEntity.ok(role);
    }
}

