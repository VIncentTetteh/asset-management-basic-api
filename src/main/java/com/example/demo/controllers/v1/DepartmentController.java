package com.example.demo.controllers.v1;

import com.example.demo.dto.DepartmentDto;
import com.example.demo.services.DepartmentService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(departmentService.create(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDto> get(@PathVariable UUID id) {
        DepartmentDto dto = departmentService.get(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<DepartmentDto>> list() {
        return ResponseEntity.ok(departmentService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDto> update(@PathVariable UUID id, @Valid @RequestBody DepartmentDto dto) {
        try {
            return ResponseEntity.ok(departmentService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
