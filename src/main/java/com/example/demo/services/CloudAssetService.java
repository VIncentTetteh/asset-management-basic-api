package com.example.demo.services;

import com.example.demo.dto.CloudAssetDto;
import com.example.demo.dto.CloudCostSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CloudAssetService {

    CloudAssetDto create(CloudAssetDto dto);

    Page<CloudAssetDto> list(String provider, String environment, Pageable pageable);

    CloudAssetDto getById(UUID id);

    CloudAssetDto update(UUID id, CloudAssetDto dto);

    void delete(UUID id);

    /** Cost summary with breakdown by provider and environment */
    CloudCostSummaryDto getCostSummary();

    /** Record a monthly cost entry for a cloud asset */
    void recordMonthlyCost(UUID assetId, String billingMonth, java.math.BigDecimal amount, String serviceName);
}
