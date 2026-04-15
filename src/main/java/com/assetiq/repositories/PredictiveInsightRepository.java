package com.assetiq.repositories;

import com.assetiq.enums.InsightSeverity;
import com.assetiq.enums.InsightType;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.models.PredictiveInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictiveInsightRepository extends JpaRepository<PredictiveInsight, UUID> {

    List<PredictiveInsight> findByOrganisationAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    List<PredictiveInsight> findByOrganisationAndResolvedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Organisation organisation);

    List<PredictiveInsight> findByOrganisationAndInsightTypeAndDeletedAtIsNull(Organisation organisation, InsightType type);

    List<PredictiveInsight> findByOrganisationAndSeverityAndDeletedAtIsNull(Organisation organisation, InsightSeverity severity);

    Optional<PredictiveInsight> findByIdAndOrganisationAndDeletedAtIsNull(UUID id, Organisation organisation);

    boolean existsByAssetAndInsightTypeAndResolvedFalseAndDeletedAtIsNull(Asset asset, InsightType type);

    @Modifying
    @Query("DELETE FROM PredictiveInsight p WHERE p.asset = :asset AND p.insightType = :type " +
           "AND p.resolved = false AND p.deletedAt IS NULL")
    void deleteUnresolvedByAssetAndType(@Param("asset") Asset asset, @Param("type") InsightType type);

    long countByOrganisationAndResolvedFalseAndDeletedAtIsNull(Organisation organisation);

    long countByOrganisationAndSeverityAndResolvedFalseAndDeletedAtIsNull(Organisation organisation, InsightSeverity severity);
}
