package com.example.demo.repositories;

import com.example.demo.models.Asset;
import com.example.demo.models.Department;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findAllByDeletedAtIsNull();
    Optional<Asset> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Asset> findByNameAndOrganisationAndDeletedAtIsNull(String name, Organisation organisation);

    Optional<Asset> findByNameIgnoreCaseAndDeletedAtIsNull(String name);
    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
            String name, Organisation organisation, Department department);
}
