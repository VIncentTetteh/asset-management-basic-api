package com.example.demo.repositories.compliance;

import com.example.demo.models.Organisation;
import com.example.demo.models.compliance.SlaMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaMetricRepository extends JpaRepository<SlaMetric, UUID> {

    List<SlaMetric> findByOrganisationAndDeletedAtIsNullOrderByYearDescMonthDesc(Organisation organisation);

    Optional<SlaMetric> findByOrganisationAndYearAndMonthAndDeletedAtIsNull(
            Organisation organisation, int year, int month);

    Optional<SlaMetric> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    @Query("SELECT AVG(s.uptimePercent) FROM SlaMetric s WHERE s.organisation = :org AND s.deletedAt IS NULL AND s.year = :year")
    Double averageUptimeForYear(Organisation org, int year);
}
