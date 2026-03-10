package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.RiskRegister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRegisterRepository extends JpaRepository<RiskRegister, UUID> {

    Page<RiskRegister> findByOrganisationAndDeletedAtIsNull(Organisation organisation, Pageable pageable);

    List<RiskRegister> findByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, RiskRegister.RiskStatus status);

    long countByOrganisationAndStatusAndDeletedAtIsNull(
            Organisation organisation, RiskRegister.RiskStatus status);

    Optional<RiskRegister> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
