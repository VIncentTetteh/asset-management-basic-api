package com.assetiq.repositories;

import com.assetiq.enums.ExpenseStatus;
import com.assetiq.models.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {
    List<Expense> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<Expense> findByOrganisationAndStatusAndDeletedAtIsNull(Organisation org, ExpenseStatus status);
    List<Expense> findBySubmittedByAndDeletedAtIsNull(User user);
    List<Expense> findByLinkedAssetAndDeletedAtIsNull(Asset asset);
    Optional<Expense> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation org);
    Page<Expense> findByLinkedBudgetAndOrganisationAndDeletedAtIsNull(
            Budget budget, Organisation org, Pageable pageable);
}
