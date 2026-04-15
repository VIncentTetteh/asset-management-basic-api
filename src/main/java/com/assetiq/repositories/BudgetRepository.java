package com.assetiq.repositories;

import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(Organisation organisation);

    Optional<Budget> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<Budget> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);
}
