package com.example.demo.repositories;

import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findAllByDeletedAtIsNull();
    Optional<Department> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Department> findByNameIgnoreCaseAndDeletedAtIsNull(String name);
    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    // Organisation-scoped queries
    List<Department> findAllByOrganisationAndDeletedAtIsNull(Organisation organisation);
    Optional<Department> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
    Optional<Department> findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);
    boolean existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);
}
