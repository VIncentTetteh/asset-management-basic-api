package com.assetiq.repositories;

import com.assetiq.models.Budget;
import com.assetiq.models.BudgetLedgerEntry;
import com.assetiq.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetLedgerEntryRepository extends JpaRepository<BudgetLedgerEntry, UUID> {

    List<BudgetLedgerEntry> findByBudgetAndOrganisationOrderByCreatedAtAsc(Budget budget, Organisation organisation);

    boolean existsByOrganisationAndIdempotencyKey(Organisation organisation, String idempotencyKey);
}
