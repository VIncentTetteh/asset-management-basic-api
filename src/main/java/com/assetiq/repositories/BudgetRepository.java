package com.assetiq.repositories;

import com.assetiq.models.Budget;
import com.assetiq.models.Organisation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Loads a budget with a row lock ({@code SELECT ... FOR UPDATE}) so concurrent
     * changes to its running totals serialise instead of overwriting each other.
     * Soft-deleted budgets are included on purpose: releasing or reversing an
     * amount against one must be able to see it (and decide to skip it).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Budget b WHERE b.id = :id AND b.organisation = :org")
    Optional<Budget> findByIdForUpdate(@Param("id") UUID id, @Param("org") Organisation org);

    List<Budget> findByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    @Query("SELECT b FROM Budget b WHERE b.deletedAt IS NULL " +
            "AND b.totalAmount IS NOT NULL AND b.totalAmount > 0 " +
            "AND b.spentAmount IS NOT NULL")
    Page<Budget> findActiveWithSpend(Pageable pageable);
}
