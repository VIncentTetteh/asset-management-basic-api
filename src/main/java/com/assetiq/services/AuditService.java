package com.assetiq.services;

import com.assetiq.dto.AssetAuditDto;
import com.assetiq.dto.AuditItemDiscrepancyRequest;
import com.assetiq.dto.AuditItemDto;
import com.assetiq.dto.AuditItemVerifyRequest;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.enums.AuditDiscrepancyType;
import com.assetiq.enums.AuditItemStatus;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface AuditService {
    AssetAuditDto createAudit(AssetAuditDto auditDto);
    AssetAuditDto getAuditById(UUID id);
    Set<AssetAuditDto> getAuditsByOrganisation(UUID organisationId);
    Set<AssetAuditDto> getAuditsByDepartment(UUID departmentId);
    Set<AssetAuditDto> getAuditsByDateRange(LocalDate startDate, LocalDate endDate);
    Set<AssetAuditDto> getAuditsByConductor(UUID userId);
    AssetAuditDto updateAuditStatus(UUID auditId, String status);

    /** Replaces the remarks of an open audit (not COMPLETED or CANCELLED); blank clears them. */
    AssetAuditDto updateAuditRemarks(UUID auditId, String remarks);

    /**
     * The organisation's audits matching every given filter (null = any), newest
     * audit date first. Filters combine with AND.
     */
    java.util.List<AssetAuditDto> searchAudits(UUID departmentId, LocalDate startDate, LocalDate endDate,
                                               UUID conductedById, com.assetiq.enums.AuditStatus status);
    void deleteAudit(UUID id); // Note: deletion should be prevented for immutable audits in production

    // ── Count sheet ──────────────────────────────────────────────────────────

    /**
     * Generates the audit's count sheet: one item per in-scope asset that does not
     * already have one. Scope is the audit's department, or the whole organisation
     * when the audit has none; disposed assets are left out because they are not
     * meant to be there.
     *
     * <p>Idempotent — running it again tops the sheet up rather than doubling it.
     *
     * @return the audit, with its progress counters refreshed.
     */
    AssetAuditDto generateAuditItems(UUID auditId);

    /** One page of an audit's items; every filter is optional and they combine (AND). */
    PagedResponseDto<AuditItemDto> listAuditItems(UUID auditId, AuditItemStatus status,
                                                  AuditDiscrepancyType discrepancyType, String search,
                                                  Integer page, Integer size);

    /** Records that a scanned or typed asset was sighted. */
    AuditItemDto verifyAuditItem(UUID auditId, AuditItemVerifyRequest request);

    /** Flags one item as wrong, with the kind of problem and a reason. */
    AuditItemDto flagAuditItemDiscrepancy(UUID auditId, UUID itemId, AuditItemDiscrepancyRequest request);
}

