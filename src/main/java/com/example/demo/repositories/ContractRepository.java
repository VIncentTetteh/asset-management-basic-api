package com.example.demo.repositories;

import com.example.demo.models.Contract;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findByOrganisationAndDeletedAtIsNullOrderByEndDateAsc(Organisation organisation);

    Optional<Contract> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    List<Contract> findBySupplierIdAndDeletedAtIsNull(UUID supplierId);

    List<Contract> findByAssetIdAndDeletedAtIsNull(UUID assetId);

    @Query("SELECT c FROM Contract c WHERE c.organisation = :org AND c.deletedAt IS NULL AND c.endDate <= :cutoff AND c.status NOT IN ('EXPIRED','TERMINATED')")
    List<Contract> findExpiringSoon(@Param("org") Organisation org, @Param("cutoff") LocalDate cutoff);
}
