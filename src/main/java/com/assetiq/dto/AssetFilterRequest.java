package com.assetiq.dto;

import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AssetType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Query parameters for GET /api/v1/assets.
 * All fields are optional; present fields are combined with AND logic.
 * Replaces the previous mutually-exclusive status/departmentId/categoryId params.
 */
public record AssetFilterRequest(
    String search,

    AssetStatus    status,
    AssetCondition condition,
    AssetType      assetType,

    UUID departmentId,
    UUID categoryId,
    UUID locationId,
    UUID assignedUserId,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate purchaseDateFrom,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate purchaseDateTo,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate warrantyExpiryBefore,

    Integer page,
    Integer size,
    String  sort
) {}
