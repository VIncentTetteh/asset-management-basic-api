package com.assetiq.services;

import com.assetiq.dto.DepartmentDto;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {
    DepartmentDto create(DepartmentDto dto);
    DepartmentDto get(UUID id);
    List<DepartmentDto> list();
    DepartmentDto update(UUID id, DepartmentDto dto);
    DepartmentDto patch(UUID id, DepartmentDto dto);
    void delete(UUID id);
}
