package com.example.demo.repositories;

import com.example.demo.models.Budget;
import com.example.demo.models.Organisation;
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
