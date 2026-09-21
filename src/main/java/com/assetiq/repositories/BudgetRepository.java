package com.assetiq.repositories;

import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(Organisation organisation);

    Optional<Budget> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<Budget> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    @Query("SELECT b FROM Budget b WHERE b.deletedAt IS NULL " +
            "AND b.totalAmount IS NOT NULL AND b.totalAmount > 0 " +
            "AND b.spentAmount IS NOT NULL")
    Page<Budget> findActiveWithSpend(Pageable pageable);
}
