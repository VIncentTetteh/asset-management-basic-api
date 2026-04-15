package com.assetiq.repositories;

import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
        List<Department> findAllByDeletedAtIsNull();

        List<Department> findAllByCreatedByAndDeletedAtIsNull(String createdBy);

        Optional<Department> findByIdAndDeletedAtIsNull(UUID id);

        Optional<Department> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

        boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

        // Organisation-scoped queries
        List<Department> findAllByOrganisationAndDeletedAtIsNull(Organisation organisation);

        List<Department> findAllByOrganisationAndCreatedByAndDeletedAtIsNull(Organisation organisation,
                        String createdBy);

        List<Department> findAllByOrganisationAndCreatedByInOrCreatedByIsNullAndDeletedAtIsNull(
                        Organisation organisation,
                        java.util.Collection<String> createdByList);

        Optional<Department> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

        Optional<Department> findByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name,
                        Organisation organisation);

        boolean existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

        boolean existsByDepartmentCodeIgnoreCaseAndOrganisationAndDeletedAtIsNull(String departmentCode, Organisation organisation);

        boolean existsByCostCenterCodeIgnoreCaseAndOrganisationAndDeletedAtIsNull(String costCenterCode, Organisation organisation);

        boolean existsByDepartmentCodeIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(String departmentCode, Organisation organisation, UUID id);

        boolean existsByCostCenterCodeIgnoreCaseAndOrganisationAndDeletedAtIsNullAndIdNot(String costCenterCode, Organisation organisation, UUID id);
}
