package com.example.demo.repositories;

import com.example.demo.models.DiscoveredDevice;
import com.example.demo.models.Organisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscoveredDeviceRepository extends JpaRepository<DiscoveredDevice, UUID> {

    List<DiscoveredDevice> findByOrganisationAndDeletedAtIsNull(Organisation organisation);

    Page<DiscoveredDevice> findByOrganisationAndDeletedAtIsNullOrderByLastSeenAtDesc(
            Organisation organisation, Pageable pageable);

    Optional<DiscoveredDevice> findByIpAddressAndOrganisationAndDeletedAtIsNull(
            String ipAddress, Organisation organisation);

    Optional<DiscoveredDevice> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);
}
