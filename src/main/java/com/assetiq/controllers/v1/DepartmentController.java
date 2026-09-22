package com.assetiq.controllers.v1;

import com.assetiq.dto.DepartmentDto;
import com.assetiq.services.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPARTMENTS')")
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(departmentService.create(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN','VIEW_DEPARTMENTS','MANAGE_DEPARTMENTS')")
    public ResponseEntity<DepartmentDto> get(@PathVariable UUID id) {
        DepartmentDto dto = departmentService.get(id);
        if (dto == null) throw new com.assetiq.exceptions.ResourceNotFoundException("Department not found");
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN','VIEW_DEPARTMENTS','MANAGE_DEPARTMENTS')")
    public ResponseEntity<List<DepartmentDto>> list() {
        return ResponseEntity.ok(departmentService.list());
    }

    @GetMapping("/{parentId}/sub-departments")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_USER','ROLE_ADMIN','VIEW_DEPARTMENTS','MANAGE_DEPARTMENTS')")
    public ResponseEntity<List<DepartmentDto>> listSubDepartments(@PathVariable UUID parentId) {
        // Unknown department -> 404 (ResourceNotFoundException); bad input -> 400.
        return ResponseEntity.ok(departmentService.listSubDepartments(parentId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPARTMENTS')")
    public ResponseEntity<DepartmentDto> update(@PathVariable UUID id, @Valid @RequestBody DepartmentDto dto) {
        // Unknown department -> 404 (ResourceNotFoundException); bad input -> 400.
        return ResponseEntity.ok(departmentService.update(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPARTMENTS')")
    public ResponseEntity<DepartmentDto> patch(@PathVariable UUID id, @RequestBody DepartmentDto dto) {
        // Unknown department -> 404 (ResourceNotFoundException); bad input -> 400.
        return ResponseEntity.ok(departmentService.patch(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ORG_ADMIN') or hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_DEPARTMENTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
