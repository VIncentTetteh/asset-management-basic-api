package com.assetiq.repositories;

import com.assetiq.enums.ExpenseStatus;
import com.assetiq.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
    List<Expense> findByOrganisationAndDeletedAtIsNull(Organisation org);
    List<Expense> findByOrganisationAndStatusAndDeletedAtIsNull(Organisation org, ExpenseStatus status);
    List<Expense> findBySubmittedByAndDeletedAtIsNull(User user);
    List<Expense> findByLinkedAssetAndDeletedAtIsNull(Asset asset);
    Optional<Expense> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation org);
}
