package com.assetiq.services;

import com.assetiq.dto.VendorPerformanceReviewDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface VendorPerformanceService {

    VendorPerformanceReviewDto create(VendorPerformanceReviewDto dto);

    VendorPerformanceReviewDto getById(UUID id);

    List<VendorPerformanceReviewDto> listAll();

    List<VendorPerformanceReviewDto> listBySupplier(UUID supplierId);

    /** Returns avg rating and review count for a supplier. */
    Map<String, Object> getSupplierSummary(UUID supplierId);

    VendorPerformanceReviewDto update(UUID id, VendorPerformanceReviewDto dto);

    void delete(UUID id);
}
